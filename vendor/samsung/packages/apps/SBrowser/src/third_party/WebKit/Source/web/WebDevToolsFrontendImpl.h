/*
 * Copyright (C) 2010 Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef WebDevToolsFrontendImpl_h
#define WebDevToolsFrontendImpl_h

#include "WebDevToolsFrontend.h"
#include "platform/Timer.h"
#include "wtf/Deque.h"
#include "wtf/Forward.h"
#include "wtf/Noncopyable.h"
#include "wtf/Vector.h"
#include "wtf/text/WTFString.h"

namespace blink {

class WebDevToolsClientDelegate;
class WebViewImpl;
struct WebDevToolsMessageData;

using WTF::String;

class WebDevToolsFrontendImpl FINAL : public blink::WebDevToolsFrontend {
    WTF_MAKE_NONCOPYABLE(WebDevToolsFrontendImpl);
public:
    WebDevToolsFrontendImpl(
        blink::WebViewImpl* webViewImpl,
        blink::WebDevToolsFrontendClient* client,
        const String& applicationLocale);
    virtual ~WebDevToolsFrontendImpl();

    // WebDevToolsFrontend implementation.
    virtual void dispatchOnInspectorFrontend(const WebString& message) OVERRIDE;

private:
    class InspectorFrontendResumeObserver;
    void resume();
    void maybeDispatch(WebCore::Timer<WebDevToolsFrontendImpl>*);
    void doDispatchOnInspectorFrontend(const WebString& message);

    blink::WebViewImpl* m_webViewImpl;
    blink::WebDevToolsFrontendClient* m_client;
    String m_applicationLocale;
    OwnPtr<InspectorFrontendResumeObserver> m_inspectorFrontendResumeObserver;
    Deque<WebString> m_messages;
    WebCore::Timer<WebDevToolsFrontendImpl> m_inspectorFrontendDispatchTimer;
};

} // namespace blink

#endif