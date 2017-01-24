/*
 * Copyright (C) 2010 Google Inc.  All rights reserved.
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

#ifndef WebSocketHandshakeRequest_h
#define WebSocketHandshakeRequest_h

#include "platform/network/HTTPRequest.h"

namespace WebCore {

class HTTPHeaderMap;

class PLATFORM_EXPORT WebSocketHandshakeRequest : public HTTPRequest {
public:
    static PassRefPtr<WebSocketHandshakeRequest> create(const String& requestMethod, const KURL& url) { return adoptRef(new WebSocketHandshakeRequest(requestMethod, url)); }
    static PassRefPtr<WebSocketHandshakeRequest> create() { return adoptRef(new WebSocketHandshakeRequest); }
    virtual ~WebSocketHandshakeRequest();

    void addAndMergeHeader(const AtomicString& name, const AtomicString& value) { addAndMergeHeader(&m_headerFields, name, value); }

    // Merges the existing value with |value| in |map| if |map| already has |name|.
    // Associates |value| with |name| in |map| otherwise.
    // This function builds data for inspector.
    static void addAndMergeHeader(HTTPHeaderMap* /* map */, const AtomicString& name, const AtomicString& value);

private:
    WebSocketHandshakeRequest(const String& requestMethod, const KURL&);
    WebSocketHandshakeRequest();
};

} // namespace WebCore

#endif // WebSocketHandshakeRequest_h