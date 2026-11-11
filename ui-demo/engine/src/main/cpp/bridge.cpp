// SPDX-License-Identifier: Apache-2.0
// Narrow read-only JNI adapter around the pinned AOSP LatinIME decoder.
#include <jni.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "defines.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/layout/proximity_info.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/session/dic_traverse_session.h"
#include "suggest/core/suggest_options.h"
#include "utils/autocorrection_threshold_utils.h"
using namespace latinime;
namespace {
struct Engine {
    std::unique_ptr<Dictionary> dictionary;
    std::unique_ptr<DicTraverseSession> session;
    std::unique_ptr<ProximityInfo> proximity;
};
std::vector<int> ints(JNIEnv* env, jintArray a) {
    std::vector<int> v(env->GetArrayLength(a));
    if (!v.empty()) env->GetIntArrayRegion(a, 0, v.size(), v.data());
    return v;
}
jintArray array(JNIEnv* env, const std::vector<int>& a) {
    auto out = env->NewIntArray(a.size());
    if (out && !a.empty()) env->SetIntArrayRegion(out, 0, a.size(), a.data());
    return out;
}
}  // namespace
extern "C" JNIEXPORT jlong JNICALL Java_hu_keyboard_engine_LatinImeEngine_openNative(
    JNIEnv* env, jclass, jstring path, jstring locale, jlong size) {
    const char* filename = env->GetStringUTFChars(path, nullptr);
    if (!filename) return 0;
    auto policy = DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
        filename, 0, size, false);
    env->ReleaseStringUTFChars(path, filename);
    if (!policy) return 0;
    auto e = std::make_unique<Engine>();
    e->dictionary = std::make_unique<Dictionary>(env, std::move(policy));
    e->session.reset(static_cast<DicTraverseSession*>(
        DicTraverseSession::getSessionInstance(env, locale, size)));
    return reinterpret_cast<jlong>(e.release());
}
extern "C" JNIEXPORT void JNICALL Java_hu_keyboard_engine_LatinImeEngine_closeNative(JNIEnv*,
                                                                                     jclass,
                                                                                     jlong handle) {
    delete reinterpret_cast<Engine*>(handle);
}
extern "C" JNIEXPORT void JNICALL Java_hu_keyboard_engine_LatinImeEngine_layoutNative(
    JNIEnv* env, jclass, jlong handle, jint width, jint height, jintArray codes,
    jintArray rectangles) {
    auto e = reinterpret_cast<Engine*>(handle);
    auto cp = ints(env, codes), r = ints(env, rectangles);
    int count = cp.size();
    if (!e || count == 0 || count > 64 || r.size() != count * 4 || width <= 0 || height <= 0)
        return;
    std::vector<int> xs(count), ys(count), ws(count), hs(count);
    std::vector<float> cx(count), cy(count), rad(count);
    for (int i = 0; i < count; i++) {
        xs[i] = r[4 * i];
        ys[i] = r[4 * i + 1];
        ws[i] = r[4 * i + 2];
        hs[i] = r[4 * i + 3];
        cx[i] = xs[i] + ws[i] * 0.5f;
        cy[i] = ys[i] + hs[i] * 0.5f;
        rad[i] = ws[i] * 0.75f;
    }
    constexpr int gw = 12, gh = 6;
    std::vector<int> grid(gw * gh * MAX_PROXIMITY_CHARS_SIZE, NOT_A_CODE_POINT);
    for (int y = 0; y < gh; y++)
        for (int x = 0; x < gw; x++) {
            float px = (x + 0.5f) * width / gw, py = (y + 0.5f) * height / gh;
            std::vector<std::pair<float, int>> near;
            for (int i = 0; i < count; i++) {
                float dx = std::max(0.f, std::max(xs[i] - px, px - xs[i] - ws[i]));
                float dy = std::max(0.f, std::max(ys[i] - py, py - ys[i] - hs[i]));
                float d = dx * dx + dy * dy;
                if (d < 150 * 150) near.emplace_back(d, cp[i]);
            }
            std::sort(near.begin(), near.end());
            for (int i = 0; i < std::min<int>(near.size(), MAX_PROXIMITY_CHARS_SIZE); i++)
                grid[(y * gw + x) * MAX_PROXIMITY_CHARS_SIZE + i] = near[i].second;
        }
    auto jgrid = array(env, grid), jx = array(env, xs), jy = array(env, ys), jw = array(env, ws),
         jh = array(env, hs);
    auto jcx = env->NewFloatArray(count), jcy = env->NewFloatArray(count),
         jr = env->NewFloatArray(count);
    env->SetFloatArrayRegion(jcx, 0, count, cx.data());
    env->SetFloatArrayRegion(jcy, 0, count, cy.data());
    env->SetFloatArrayRegion(jr, 0, count, rad.data());
    e->proximity = std::make_unique<ProximityInfo>(env, width, height, gw, gh, 100, 129, jgrid,
                                                   count, jx, jy, jw, jh, codes, jcx, jcy, jr);
    for (auto a : {jgrid, jx, jy, jw, jh}) env->DeleteLocalRef(a);
    for (auto a : {jcx, jcy, jr}) env->DeleteLocalRef(a);
}
extern "C" JNIEXPORT jint JNICALL Java_hu_keyboard_engine_LatinImeEngine_probabilityNative(
    JNIEnv* env, jclass, jlong handle, jintArray word) {
    auto e = reinterpret_cast<Engine*>(handle);
    auto cp = ints(env, word);
    return e ? e->dictionary->getProbability(CodePointArrayView(cp)) : NOT_A_PROBABILITY;
}
extern "C" JNIEXPORT void JNICALL Java_hu_keyboard_engine_LatinImeEngine_suggestNative(
    JNIEnv* env, jclass, jlong handle, jintArray word, jintArray x, jintArray y, jintArray previous,
    jintArray outCount, jintArray outWords, jintArray outScores) {
    auto e = reinterpret_cast<Engine*>(handle);
    auto cp = ints(env, word), xs = ints(env, x), ys = ints(env, y), prev = ints(env, previous);
    if (!e || !e->proximity || cp.size() >= MAX_WORD_LENGTH || xs.size() != cp.size() ||
        ys.size() != cp.size() || prev.size() >= MAX_WORD_LENGTH)
        return;
    std::vector<int> times(cp.size()), pointers(cp.size(), 0);
    for (int i = 0; i < times.size(); i++) times[i] = i * 100;
    NgramContext context(prev.data(), prev.size(), false);
    const int options[] = {0, 0, 1, 0, 1000};
    SuggestOptions opts(options, 5);
    SuggestionResults results(MAX_RESULTS);
    if (cp.empty())
        e->dictionary->getPredictions(&context, &results);
    else
        e->dictionary->getSuggestions(e->proximity.get(), e->session.get(), xs.data(), ys.data(),
                                      times.data(), pointers.data(), cp.data(), cp.size(), &context,
                                      &opts, NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL, &results);
    auto spaces = env->NewIntArray(MAX_RESULTS), types = env->NewIntArray(MAX_RESULTS),
         confidence = env->NewIntArray(1);
    auto weight = env->NewFloatArray(1);
    results.outputSuggestions(env, outCount, outWords, outScores, spaces, types, confidence,
                              weight);
    env->DeleteLocalRef(spaces);
    env->DeleteLocalRef(types);
    env->DeleteLocalRef(confidence);
    env->DeleteLocalRef(weight);
}

extern "C" JNIEXPORT jfloat JNICALL Java_hu_keyboard_engine_LatinImeEngine_normalizedNative(
    JNIEnv* env, jclass, jintArray before, jintArray after, jint score) {
    auto a = ints(env, before), b = ints(env, after);
    return AutocorrectionThresholdUtils::calcNormalizedScore(a.data(), a.size(), b.data(), b.size(),
                                                             score);
}
