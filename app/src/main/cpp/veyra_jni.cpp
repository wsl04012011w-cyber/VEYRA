#include <jni.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex g_engine_mutex;
std::atomic<bool> g_stop{false};
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
bool g_backend_initialized = false;

void release_model_locked() {
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

void callback_string(JNIEnv * env, jobject callback, const char * method, const std::string & value) {
    jclass cls = env->GetObjectClass(callback);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, method, "(Ljava/lang/String;)V");
    if (mid) {
        jstring text = env->NewStringUTF(value.c_str());
        if (text) {
            env->CallVoidMethod(callback, mid, text);
            env->DeleteLocalRef(text);
        }
    }
    env->DeleteLocalRef(cls);
}

void callback_complete(JNIEnv * env, jobject callback) {
    jclass cls = env->GetObjectClass(callback);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onComplete", "()V");
    if (mid) env->CallVoidMethod(callback, mid);
    env->DeleteLocalRef(cls);
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_veyra_app_nativeengine_NativeLlama_nativeLoadModel(
        JNIEnv * env, jclass, jstring model_path) {
    if (!model_path) return env->NewStringUTF("Caminho do modelo vazio.");

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return env->NewStringUTF("Não foi possível ler o caminho do modelo.");

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    g_stop.store(true);
    release_model_locked();

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // Force all model layers to remain on CPU.
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_model) return env->NewStringUTF("Não foi possível carregar o GGUF. Verifique o arquivo e a memória disponível.");

    auto context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 256;
    // CPU-only mobile profile: explicitly use six llama.cpp worker threads.
    // Android's scheduler may still migrate workers or reduce clocks thermally.
    constexpr int kInferenceThreads = 6;
    constexpr int kBatchThreads = 6;
    context_params.n_threads = kInferenceThreads;
    context_params.n_threads_batch = kBatchThreads;

    g_context = llama_init_from_model(g_model, context_params);
    if (!g_context) {
        release_model_locked();
        return env->NewStringUTF("O modelo abriu, mas não foi possível criar o contexto de inferência.");
    }
    g_stop.store(false);
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_com_veyra_app_nativeengine_NativeLlama_nativeUnloadModel(JNIEnv *, jclass) {
    g_stop.store(true);
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    release_model_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_veyra_app_nativeengine_NativeLlama_nativeStop(JNIEnv *, jclass) {
    g_stop.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_veyra_app_nativeengine_NativeLlama_nativeGenerate(
        JNIEnv * env, jclass, jstring prompt_text, jobject callback) {
    if (!prompt_text || !callback) return;
    const char * raw_prompt = env->GetStringUTFChars(prompt_text, nullptr);
    if (!raw_prompt) return;
    const std::string user_prompt(raw_prompt);
    env->ReleaseStringUTFChars(prompt_text, raw_prompt);

    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (!g_model || !g_context) {
        callback_string(env, callback, "onError", "Nenhum modelo está carregado.");
        callback_complete(env, callback);
        return;
    }

    g_stop.store(false);
    llama_memory_clear(llama_get_memory(g_context), true);

    const char * chat_template = llama_model_chat_template(g_model, nullptr);
    if (!chat_template) {
        callback_string(env, callback, "onError", "Este modelo não possui um template de conversa reconhecido.");
        callback_complete(env, callback);
        return;
    }

    llama_chat_message message = {"user", user_prompt.c_str()};
    std::vector<char> formatted(std::max<size_t>(4096, user_prompt.size() * 4 + 1024));
    int32_t formatted_size = llama_chat_apply_template(
        chat_template, &message, 1, true, formatted.data(), static_cast<int32_t>(formatted.size()));
    if (formatted_size < 0) {
        callback_string(env, callback, "onError", "Falha ao formatar a conversa para este modelo.");
        callback_complete(env, callback);
        return;
    }
    if (static_cast<size_t>(formatted_size) >= formatted.size()) {
        formatted.resize(static_cast<size_t>(formatted_size) + 1);
        formatted_size = llama_chat_apply_template(
            chat_template, &message, 1, true, formatted.data(), static_cast<int32_t>(formatted.size()));
    }
    if (formatted_size <= 0) {
        callback_string(env, callback, "onError", "Prompt vazio ou inválido.");
        callback_complete(env, callback);
        return;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int32_t token_count = llama_tokenize(vocab, formatted.data(), formatted_size, nullptr, 0, false, true);
    if (token_count >= 0) {
        callback_string(env, callback, "onError", "Não foi possível dimensionar a tokenização do prompt.");
        callback_complete(env, callback);
        return;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(-token_count));
    token_count = llama_tokenize(vocab, formatted.data(), formatted_size, tokens.data(),
                                 static_cast<int32_t>(tokens.size()), false, true);
    if (token_count <= 0 || static_cast<uint32_t>(token_count) >= llama_n_ctx(g_context) - 8) {
        callback_string(env, callback, "onError", "A mensagem excede o contexto disponível de 2048 tokens.");
        callback_complete(env, callback);
        return;
    }

    int32_t prompt_offset = 0;
    while (prompt_offset < token_count) {
        const int32_t chunk_size = std::min(256, token_count - prompt_offset);
        llama_batch prompt_batch = llama_batch_init(chunk_size, 0, 1);
        prompt_batch.n_tokens = chunk_size;
        for (int32_t i = 0; i < chunk_size; ++i) {
            const int32_t token_index = prompt_offset + i;
            prompt_batch.token[i] = tokens[static_cast<size_t>(token_index)];
            prompt_batch.pos[i] = token_index;
            prompt_batch.n_seq_id[i] = 1;
            prompt_batch.seq_id[i][0] = 0;
            prompt_batch.logits[i] = (token_index == token_count - 1);
        }
        const int decode_result = llama_decode(g_context, prompt_batch);
        llama_batch_free(prompt_batch);
        if (decode_result != 0) {
            callback_string(env, callback, "onError", "llama_decode falhou ao processar o prompt (código " + std::to_string(decode_result) + ").");
            callback_complete(env, callback);
            return;
        }
        prompt_offset += chunk_size;
    }

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_init(1, 0, 1);
    batch.n_tokens = 1;
    int32_t generated = 0;
    while (!g_stop.load() && generated < 512 && token_count + generated < static_cast<int32_t>(llama_n_ctx(g_context)) - 1) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char piece[512];
        const int32_t piece_size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (piece_size > 0) {
            callback_string(env, callback, "onToken", std::string(piece, static_cast<size_t>(piece_size)));
        } else if (piece_size < 0) {
            callback_string(env, callback, "onError", "Token excedeu o buffer de saída.");
            break;
        }

        batch.token[0] = token;
        batch.pos[0] = token_count + generated;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        const int result = llama_decode(g_context, batch);
        if (result != 0) {
            callback_string(env, callback, "onError", "Falha durante a geração de tokens.");
            break;
        }
        ++generated;
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);
    callback_complete(env, callback);
}
