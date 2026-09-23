package tw.xiaoxin.relens;

import android.app.*;import android.content.Intent;import com.google.android.gms.auth.api.identity.*;import com.google.android.gms.common.api.*;import com.google.android.gms.common.api.Scope;import java.util.*;

final class YouTubeAuthManager {
    static final int REQUEST_AUTH=710;interface Listener{void onToken(String token);void onError(String message);}
    private final Activity activity;private final AuthorizationClient client;private final Listener listener;
    YouTubeAuthManager(Activity a,Listener l){activity=a;listener=l;client=Identity.getAuthorizationClient(a);}
    void authorize(){AuthorizationRequest request=AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/youtube"))).build();client.authorize(request).addOnSuccessListener(result->{if(result.hasResolution()){try{activity.startIntentSenderForResult(result.getPendingIntent().getIntentSender(),REQUEST_AUTH,null,0,0,0);}catch(Exception e){listener.onError(e.getMessage());}}else deliver(result);}).addOnFailureListener(e->listener.onError(e.getMessage()));}
    void onActivityResult(Intent data){try{deliver(client.getAuthorizationResultFromIntent(data));}catch(ApiException e){listener.onError(e.getMessage());}}
    private void deliver(AuthorizationResult result){String token=result.getAccessToken();if(token==null||token.isEmpty())listener.onError("Google 未回傳 YouTube 授權權杖");else{AppLog.i("YOUTUBE","Authorization granted");listener.onToken(token);}}
}
