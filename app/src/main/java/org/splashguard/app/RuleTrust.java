package org.splashguard.app;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/** Reviewed, compiled trust root. No file, URI, preference or network can supply a key. */
public final class RuleTrust {
    public static final String KEY_ID="splashguard.rules.20261027";
    public static final String PUBLISHER="splashguard", CHANNEL="community", PACK_ID="splashguard.community";
    public static final String PUBLIC_KEY_SHA256="b5754754ab983e6f8dacd8c5965390169df08f891c10d8ef911339851cdaa43e";
    private static final String PUBLIC_KEY="MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAzUFhgQRxtor6c7++SiK6AQb6F1Vjd+0ax3PP2bZTR2VExJI6UwboURDXI9FRRP38/8GkZ1Q70ywZCk843yO71tT2pPHeafRyjjoAyAZ+v7L7p4HF4hzWWOHwECRqsSQeT+D7lNAE9Xc21TniTA4EHb27t52dP/zu8bmLGUNOZna+p0y5MVe3I0eEUbfDuUo1M7mhsJS5zS3tpHbO82IjPmNhr0c0qlN17amjNq/LYrHsc75epb1ivK1gURUCBOIumaGlGVtLu2d7p/3mMvTPMspYZo9rJjOPqrbSJSAevg9mL/S0/uPqo+8ybxjboIZGegFATvUb8T0igcIOE8pXOdnQb3RnQYEYsSbbFSdQsMRICijbnk3EUytYmSSkpKFbTHnw9BGW1bUc6jjJ+WEezFvR+05ZjNVUuggKLyqNH4PSmdBi9sWaPbEkNlpvei9V6IHFMXuyK+vtkLjDCBmyqDUVpQotX2FtdJa2BwVm0dcYX/bCSM9pPaz7h8GL5H3BAgMBAAE=";
    private RuleTrust(){}
    private static List<SignedRulePack.TrustedKey> load(){
        try{
            byte[] encoded=Base64.getDecoder().decode(PUBLIC_KEY);
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(encoded);StringBuilder hex=new StringBuilder();
            for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!hex.toString().equals(PUBLIC_KEY_SHA256))throw new IllegalStateException();
            return Collections.singletonList(new SignedRulePack.TrustedKey(KEY_ID,PUBLISHER,CHANNEL,
                    Collections.singletonList(PACK_ID),KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded))));
        }catch(Exception invalid){throw new IllegalStateException("内置信任配置无法验证，已停止认证导入。");}
    }
    public static SignedRulePack.VerifiedPack verify(String envelope){return SignedRulePack.verify(envelope,load());}
}
