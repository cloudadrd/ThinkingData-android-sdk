package cn.dataeye.android.encrypt;

public class SecreteKey {
    //公钥
    public String publicKey;

    //公钥版本
    public String version;

    public SecreteKey(String publicKey, String version) {
        this.publicKey = publicKey;
        this.version = version;
    }
}
