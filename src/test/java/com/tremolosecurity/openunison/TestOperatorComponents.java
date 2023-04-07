package com.tremolosecurity.openunison;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tremolosecurity.openunison.crd.OpenUnison;
import com.tremolosecurity.openunison.crd.OpenUnisonSpecKeyStoreStaticKeysInner;
import com.tremolosecurity.openunison.kubernetes.ClusterConnection;
import com.tremolosecurity.openunison.obj.WsResponse;
import com.tremolosecurity.openunison.secret.Generator;

public class TestOperatorComponents {

    static ClusterConnection cluster;

    @BeforeAll
    public static void setup() {
        cluster = cluster = new ClusterConnection(System.getenv("API_SERVER_URL"),"openunison",System.getenv("PATH_TO_CA_CRT"),System.getenv("PATH_TO_TOKEN"),new String[]{"2","3","4","5","6","7"});
    }

    @Test
    public void testStaticSecretCreateNew() throws Exception {
        // delete the static secret and make sure it gets created
        com.tremolosecurity.openunison.crd.OpenUnison ou = loadOrchestra();

        // delete secret
        cluster.delete("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");

        // make sure it's not still there
        WsResponse resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(404,resp.getResult());

        Generator gensecret = new Generator();
        gensecret.load(ou,cluster,"openunison","orchestra");

        System.out.println("Sleeping for 3 seconds");
        Thread.sleep(3000);

        resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());
    }

    private String getKey(String b64secret) throws ParseException {
        String json = new String(Base64.getDecoder().decode(b64secret));
        JSONObject obj = (JSONObject) new JSONParser().parse(json);
        return (String) obj.get("key_data");
    }

    private int getVersion(String b64secret) throws ParseException {
        String json = new String(Base64.getDecoder().decode(b64secret));
        JSONObject obj = (JSONObject) new JSONParser().parse(json);
        return ((Long) obj.get("version")).intValue();
    }

    @Test
    public void testStaticPatchNewVersion() throws Exception {
        // delete the static secret and make sure it gets created
        com.tremolosecurity.openunison.crd.OpenUnison ou = loadOrchestra();

        // make sure the Secret is still there
        WsResponse resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        String curKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        int curVersion = getVersion((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        String curLastMileKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidc"));
        // make sure the current key verison is "1"
        for  (OpenUnisonSpecKeyStoreStaticKeysInner key : ou.getSpec().getKeyStore().getStaticKeys()) {
            if (key.getName().equals("session-unison")) {
                
                key.setVersion(curVersion + 1);
            }
        }

        Generator gensecret = new Generator();
        gensecret.load(ou,cluster,"openunison","orchestra");

        System.out.println("Sleeping for 3 seconds");
        Thread.sleep(3000);

        resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        String newKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        String newLastMileKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidc"));
        assertNotEquals(newKey, curKey);
        assertEquals(curLastMileKey,newLastMileKey);
    }

    @Test
    public void testStaticDeleteRemovedSecret() throws Exception {
        // delete the static secret and make sure it gets created
        com.tremolosecurity.openunison.crd.OpenUnison ou = loadOrchestra();

        // make sure the Secret is still there
        WsResponse resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        String curKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        int curVersion = getVersion((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        String curLastMileKey = (String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidc");

        assertNotNull(curLastMileKey);



        // make sure the current key verison is "1"
        OpenUnisonSpecKeyStoreStaticKeysInner lastmilekey = null;
        for  (OpenUnisonSpecKeyStoreStaticKeysInner key : ou.getSpec().getKeyStore().getStaticKeys()) {
            if (key.getName().equals("lastmile-oidc")) {
                lastmilekey = key;
            }
        }

        assertNotNull(lastmilekey);
        ou.getSpec().getKeyStore().getStaticKeys().remove(lastmilekey);

        Generator gensecret = new Generator();
        gensecret.load(ou,cluster,"openunison","orchestra");

        System.out.println("Sleeping for 3 seconds");
        Thread.sleep(3000);

        resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        
        String newLastMileKey = (String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidc");
        assertNull(newLastMileKey);
        
        // cleanup, patch the Secret
        JSONObject root = new JSONObject();
        JSONObject data = new JSONObject();
        root.put("data",data);
        data.put("lastmile-oidc",curLastMileKey);
        assertEquals(cluster.patch("/api/v1/namespaces/openunison/secrets/orchestra-static-keys", root.toJSONString()).getResult(),200);
    }

    @Test
    public void testStaticAddNewSecret() throws Exception {
        // delete the static secret and make sure it gets created
        com.tremolosecurity.openunison.crd.OpenUnison ou = loadOrchestra();

        // make sure the Secret is still there
        WsResponse resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        String curKey = getKey((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        int curVersion = getVersion((String)((JSONObject)resp.getBody().get("data")).get("session-unison"));
        String curLastMileKey = (String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidcx");

        assertNull(curLastMileKey);



        // make sure the current key verison is "1"
        OpenUnisonSpecKeyStoreStaticKeysInner lastmilexkey = new OpenUnisonSpecKeyStoreStaticKeysInner();
        lastmilexkey.setName("lastmile-oidcx");
        lastmilexkey.setVersion(1);
        
        
        ou.getSpec().getKeyStore().getStaticKeys().add(lastmilexkey);

        Generator gensecret = new Generator();
        gensecret.load(ou,cluster,"openunison","orchestra");

        System.out.println("Sleeping for 3 seconds");
        Thread.sleep(3000);

        resp = cluster.get("/api/v1/namespaces/openunison/secrets/orchestra-static-keys");
        assertEquals(200,resp.getResult());

        
        String newLastMileKey = (String)((JSONObject)resp.getBody().get("data")).get("lastmile-oidcx");
        assertNotNull(newLastMileKey);
        
        // cleanup, patch the Secret
        JSONObject root = new JSONObject();
        JSONObject data = new JSONObject();
        root.put("data",data);
        data.put("lastmile-oidcx",null);
        assertEquals(cluster.patch("/api/v1/namespaces/openunison/secrets/orchestra-static-keys", root.toJSONString()).getResult(),200);
    }

    @Test
    public void testLoadObject() throws Exception {
        com.tremolosecurity.openunison.crd.OpenUnison ou = loadOrchestra();

        assertEquals(ou.getSpec().getImage(),System.getenv("EXPECTED_IMAGE"));

        // get unison-tls-secret resource version then delete it
        String unisonTlsUid = null;
        WsResponse resp = cluster.get("/api/v1/namespaces/openunison/secrets/unison-tls");
        if (resp.getResult() == 200) {
            // the secret exists, lets get the resource version
            JSONObject metadata = (JSONObject) resp.getBody().get("metadata");
            unisonTlsUid = (String) metadata.get("uid");
        }

        assertNotNull(unisonTlsUid);
        resp = cluster.delete("/api/v1/namespaces/openunison/secrets/unison-tls");
        resp = cluster.get("/api/v1/namespaces/openunison/secrets/unison-tls");
        assertEquals(404,resp.getResult());

        // get kubernetes-dashboard-certs resource version then delete it
        String k8sDbUid = null;
        resp = cluster.get("/api/v1/namespaces/kubernetes-dashboard/secrets/kubernetes-dashboard-certs");
        if (resp.getResult() == 200) {
            // the secret exists, lets get the resource version
            JSONObject metadata = (JSONObject) resp.getBody().get("metadata");
            k8sDbUid = (String) metadata.get("uid");
        }

        String patch = "{\"metadata\":{\"labels\":{\"operated-by\":null,\"tremolo_operator_created\":null}}}";

        resp = cluster.patch("/api/v1/namespaces/kubernetes-dashboard/secrets/kubernetes-dashboard-certs",patch);
        assertEquals(200,resp.getResult());

        String k8sDbPodUid = null;
        resp = cluster.get("/api/v1/namespaces/kubernetes-dashboard/pods?labelSelector=k8s-app%3Dkubernetes-dashboard");
        if (resp.getResult() == 200) {
            // the secret exists, lets get the resource version
            JSONArray items = (JSONArray) resp.getBody().get("items");
            JSONObject pod = (JSONObject) items.get(0);
            JSONObject metadata = (JSONObject) pod.get("metadata");
            k8sDbPodUid = (String) metadata.get("uid");
        }

        

        

        Generator gensecret = new Generator();
        gensecret.load(ou,cluster,"openunison","orchestra");

        System.out.println("Sleeping for 3 seconds");
        Thread.sleep(3000);

        // validate non-secret data
        assertNotNull(gensecret.getProps().get("OPENUNISON_PROVISIONING_ENABLED"));
        assertEquals("false",gensecret.getProps().get("OPENUNISON_PROVISIONING_ENABLED"));

        assertNotNull(gensecret.getProps().get("K8S_SELF_LINK"));
        assertEquals(new URL(cluster.getWatchUrl()).getPath() + "/orchestra",gensecret.getProps().get("K8S_SELF_LINK"));
        

        //validate hosts
        assertNotNull(gensecret.getProps().get("OU_HOST"));
        assertEquals("k8sou.lab.tremolo.dev",gensecret.getProps().get("OU_HOST"));

        // validate secret data
        assertNotNull(gensecret.getProps().get("K8S_DB_SECRET"));
        assertEquals("10accf42-39c8-42fb-8690-14a838e42f65",gensecret.getProps().get("K8S_DB_SECRET"));
            
        // validate a new unison-tls secret was created
        resp = cluster.get("/api/v1/namespaces/openunison/secrets/unison-tls");
        assertEquals(200,resp.getResult());
            
        JSONObject metadata = (JSONObject) resp.getBody().get("metadata");
        String newUnisonTlsUid = (String) metadata.get("uid");
        
        assertNotEquals(unisonTlsUid,newUnisonTlsUid);

        // validate a new kubernetes-dashboard secret was created
        resp = cluster.get("/api/v1/namespaces/kubernetes-dashboard/secrets/kubernetes-dashboard-certs");
        assertEquals(200, resp.getResult());
        metadata = (JSONObject) resp.getBody().get("metadata");
        String newK8sDbUid = (String) metadata.get("uid");
        assertNotEquals(k8sDbUid,newK8sDbUid);

        // validate the kubernetes-dashboard pods were recreated:
        resp = cluster.get("/api/v1/namespaces/kubernetes-dashboard/pods?labelSelector=k8s-app%3Dkubernetes-dashboard");
        assertEquals(200,resp.getResult());
            
        JSONArray items = (JSONArray) resp.getBody().get("items");
        JSONObject pod = (JSONObject) items.get(0);
        metadata = (JSONObject) pod.get("metadata");
        String newK8sDbPodUid = (String) metadata.get("uid");
        assertNotEquals(k8sDbPodUid, newK8sDbPodUid);
        

    }

    private com.tremolosecurity.openunison.crd.OpenUnison loadOrchestra()
            throws Exception, URISyntaxException, IOException, InterruptedException, ParseException {
        cluster.findVersion();
        JSON.setGson(JSON.createGson().create());
        

        SSLContext sslCtx = cluster.generateSSLContext();
        HttpClient http = HttpClient.newBuilder()
                            .sslContext(sslCtx)
                            .build();

        HttpRequest get = HttpRequest.newBuilder()
                            .uri(new URI(cluster.getWatchUrl() + "/orchestra"))
                            .GET()
                            .header("Authorization", String.format("Bearer %s", cluster.loadToken()))
                            .build();

        HttpResponse<String> resp = http.send(get,BodyHandlers.ofString());

        assertEquals(resp.statusCode(),200);

        JSONObject json = (JSONObject) new JSONParser().parse(resp.body());

        json.remove("metadata");
        json.remove("kind");
        json.remove("apiVersion");

        com.tremolosecurity.openunison.crd.OpenUnison ou = JSON.getGson().fromJson(json.toString(), OpenUnison.class);
        return ou;
    }
}
