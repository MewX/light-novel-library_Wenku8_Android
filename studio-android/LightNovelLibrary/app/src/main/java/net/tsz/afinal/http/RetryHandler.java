/**
 * Copyright (c) 2012-2013, Michael Yang 杨福海 (www.yangfuhai.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tsz.afinal.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

import javax.net.ssl.SSLHandshakeException;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import android.os.SystemClock;

public class RetryHandler implements HttpRequestRetryHandler {
    private static final int RETRY_SLEEP_TIME_MILLIS = 1000;
    
    //网络异常，继续
    private static HashSet<Class<?>> exceptionWhitelist = new HashSet<Class<?>>();
    
    //用户异常，不继续（如，用户中断线程）
    private static HashSet<Class<?>> exceptionBlacklist = new HashSet<Class<?>>();

    static {
        exceptionWhitelist.add(NoHttpResponseException.class);
        exceptionWhitelist.add(UnknownHostException.class);
        exceptionWhitelist.add(SocketException.class);

        exceptionBlacklist.add(InterruptedIOException.class);
        exceptionBlacklist.add(SSLHandshakeException.class);
    }

    private final int maxRetries;

    public RetryHandler(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b.booleanValue());

        if(executionCount > maxRetries) {
            // 尝试次数超过用户定义的测试，默认5次
            retry = false;
        } else if (exceptionBlacklist.contains(exception.getClass())) {
            // 线程被用户中断，则不继续尝试
            retry = false;
        } else if (exceptionWhitelist.contains(exception.getClass())) {
            retry = true;
        } else if (!sent) {
            retry = true;
        }

        if(retry) {
            HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute( ExecutionContext.HTTP_REQUEST );
            retry = currentReq!=null && !"POST".equals(currentReq.getMethod());
        }

        if(retry) {
        	//休眠1秒钟后再继续尝试
            SystemClock.sleep(RETRY_SLEEP_TIME_MILLIS);
        } else {
            exception.printStackTrace();
        }

        return retry;
    }
    
}