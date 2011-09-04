package com.sailthru.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sailthru.client.handler.JSONHandler;
import com.sailthru.client.handler.SailthruResponseHandler;
import com.sailthru.client.http.SailthruHandler;
import com.sailthru.client.http.SailthruHttpClient;
import com.sailthru.client.params.ApiFileParams;
import com.sailthru.client.params.ApiParams;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpVersion;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * Abstract class exposing genric API calls for Sailthru API as per http://docs.sailthru.com/api
 * @author Prajwal Tuladhar <praj@sailthru.com>
 */
public abstract class AbstractSailthruClient {

    protected static final Logger logger = Logger.getLogger(AbstractSailthruClient.class.getName());

    public static final String DEFAULT_API_URL = "https://api.sailthru.com";
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int DEFAULT_HTTPS_PORT = 443;
    public static final String DEFAULT_USER_AGENT = "Sailthru Java Client";
    public static final String VERSION = "1.0";
    public static final String DEFAULT_ENCODING = "UTF-8";

    protected static enum HandlerType { JSON };    //we can also add XML but not now!
    public static enum HttpRequestMethod {GET, POST, DELETE}; //HTTP methods supported by Sailthru API

    protected String apiKey;
    protected String apiSecret;
    protected String apiUrl;

    protected SailthruHttpClient httpClient;

    private SailthruHandler handler;

    protected Gson gson;


    /**
     * Main constructor class for setting up the client
     * @param apiKey
     * @param apiSecret
     * @param apiUrl
     */
    public AbstractSailthruClient(String apiKey, String apiSecret, String apiUrl) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.apiUrl = apiUrl;
        this.handler = new SailthruHandler(new JSONHandler());
        this.httpClient = create();
        this.gson = new Gson();
    }


    /**
     * Create SailthruHttpClient
     */
    private SailthruHttpClient create() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, DEFAULT_ENCODING);
        HttpProtocolParams.setUserAgent(params, DEFAULT_USER_AGENT);
        HttpProtocolParams.setUseExpectContinue(params, true);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(getScheme());

        ThreadSafeClientConnManager connManager = new ThreadSafeClientConnManager(schemeRegistry);
        return new SailthruHttpClient(connManager, params);
    }

    /**
     * Getter for SailthruHttpClient
     */
    public SailthruHttpClient getSailthruHttpClient() {
        return httpClient;
    }


    /**
     * Get Scheme Object
     */
    protected Scheme getScheme() {
        String scheme = null;
        try {
            URI uri = new URI(this.apiUrl);
            scheme = uri.getScheme();
        }
        catch (URISyntaxException e) {
            scheme = "http";
        }
        if (scheme.equals("https")) {
            return new Scheme(scheme, DEFAULT_HTTPS_PORT, SSLSocketFactory.getSocketFactory());
        }
        else {
            return new Scheme(scheme, DEFAULT_HTTP_PORT, PlainSocketFactory.getSocketFactory());
        }
    }


    /**
     * Make Http request to Sailthru API for given resource with given method and data
     * @param action
     * @param method
     * @param data parameter data
     * @return Object
     * @throws IOException
     */
    protected Object httpRequest(ApiAction action, HttpRequestMethod method, Map<String, Object> data) throws IOException {
        String url = this.apiUrl + "/" + action.toString();

        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        String json = gson.toJson(data, type);

        Map<String, String> params = buildPayload(json);

        return this.httpClient.executeHttpRequest(url, method, params, handler);
    }

    /**
     * Make HTTP Request to Sailthru API but with Api Params rather than generalized Map, this is recommended way to make request if data structure is complex
     * @param method HTTP method
     * @param apiParams 
     * @return Object
     * @throws IOException
     */
    protected Object httpRequest(HttpRequestMethod method, ApiParams apiParams) throws IOException {
        String url = this.apiUrl + "/" + apiParams.getApiCall().toString();
        String json = gson.toJson(apiParams, apiParams.getType());
        Map<String, String> params = buildPayload(json);
        return this.httpClient.executeHttpRequest(url, method, params, handler);
    }
    
    
    /**
     * Make HTTP Request to Sailthru API involving multi-part uploads but with Api Params rather than generalized Map, this is recommended way to make request if data structure is complex
     * @param method
     * @param apiParams
     * @param fileParams
     * @return Object
     * @throws IOException 
     */
    protected Object httpRequest(HttpRequestMethod method, ApiParams apiParams, ApiFileParams fileParams) throws IOException {
        String url = this.apiUrl + "/" + apiParams.getApiCall().toString();
        String json = gson.toJson(apiParams, apiParams.getType());
        Map<String, String> params = buildPayload(json);
        return this.httpClient.executeHttpRequest(url, method, params, fileParams.getFileParams(), handler);
    }

    /**
     * Build HTTP Request Payload
     * @param jsonPayload JSON payload
     * @return Map Object
     */
    private Map<String, String> buildPayload(String jsonPayload) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("api_key", apiKey);
        params.put("format", handler.getSailthruResponseHandler().getFormat());
        params.put("json", jsonPayload);
        params.put("sig", getSignatureHash(params));
        logger.log(Level.INFO, "Params: {0}", params.toString());
        return params;
    }

    /**
     * Get Signature Hash from given Map
     */
    protected String getSignatureHash(Map<String, String> parameters) {
        List<String> values = new ArrayList<String>();

        StringBuilder data = new StringBuilder();
        data.append(this.apiSecret);

        for (Entry<String, String> entry : parameters.entrySet()) {
           values.add(entry.getValue());
        }

        Collections.sort(values);

        for( String value:values ) {
            data.append(value);
        }
        return SailthruUtil.md5(data.toString());
    }


    /**
     * HTTP GET Request with Map
     * @param action API action
     * @param data Parameter data
     * @return Object
     * @throws IOException
     */
    public Object apiGet(ApiAction action, Map<String, Object> data) throws IOException {
        return httpRequest(action, HttpRequestMethod.GET, data);
    }

    /**
     * HTTP GET Request with Interface implementation of ApiParams
     * @param data
     * @return Object
     * @throws IOException
     */
    public Object apiGet(ApiParams data) throws IOException {
        return httpRequest(HttpRequestMethod.GET, data);
    }


    /**
     * HTTP POST Request with Map
     * @param action
     * @param data
     * @return Object
     * @throws IOException
     */
    public Object apiPost(ApiAction action, Map<String, Object> data) throws IOException {
        return httpRequest(action, HttpRequestMethod.POST, data);
    }


    /**
     * HTTP POST Request with Interface implementation of ApiParams
     * @param data
     * @return Object
     * @throws IOException
     */
    public Object apiPost(ApiParams data) throws IOException {
        return httpRequest(HttpRequestMethod.POST, data);
    }
    
    
    public Object apiPost(ApiParams data, ApiFileParams fileParams) throws IOException {
        return httpRequest(HttpRequestMethod.POST, data, fileParams);
    }
    

    /**
     * HTTP DELETE Request with Map
     * @param action
     * @param data
     * @return Object
     * @throws IOException
     */
    public Object apiDelete(ApiAction action, Map<String, Object> data) throws IOException {
        return httpRequest(action, HttpRequestMethod.DELETE, data);
    }

    /**
     * HTTP DELETE Request with Interface implementation of ApiParams
     * @param data
     * @return Object
     * @throws IOException
     */
    public Object apiDelete(ApiParams data) throws IOException {
        return httpRequest(HttpRequestMethod.DELETE, data);
    }

    /**
     * Set response Handler, currently only JSON is supported but XML can also be supported later on
     * @param responseHandler
     */
    public void setResponseHandler(SailthruResponseHandler responseHandler) {
        this.handler.setSailthruResponseHandler(responseHandler);
    }
}