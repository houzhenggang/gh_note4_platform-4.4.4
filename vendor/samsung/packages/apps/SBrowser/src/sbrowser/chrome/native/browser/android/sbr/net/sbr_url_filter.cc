#include "base/android/jni_string.h"
#include "base/logging.h"
#include "jni/SbrUrlFilter_jni.h"

using base::android::AttachCurrentThread;
using base::android::ConvertUTF8ToJavaString;
using base::android::ScopedJavaGlobalRef;

namespace net {
bool SbrUrlFilterIsBlockedUrl(const std::string& url) {
  // Use Java System.getProperty to get configuration information.
  // TODO(pliard): Conversion to/from UTF8 ok here?
  JNIEnv* env = AttachCurrentThread();
  ScopedJavaLocalRef<jstring> str = ConvertUTF8ToJavaString(env, url);
  return Java_SbrUrlFilter_isBlockedUrl(env, str.obj());
}

// Register native methods    
bool RegisterSbrUrlFilter(JNIEnv* env) {
    return RegisterNativesImpl(env);
}

}