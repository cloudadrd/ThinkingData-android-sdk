package cn.dataeye.android.encrypt;

public class SecreteKey {
    //公钥
    public String publicKey;

    //公钥版本
    public int version;

    public SecreteKey(String publicKey, int version) {
        this.publicKey = publicKey;
        this.version = version;
    }
}
