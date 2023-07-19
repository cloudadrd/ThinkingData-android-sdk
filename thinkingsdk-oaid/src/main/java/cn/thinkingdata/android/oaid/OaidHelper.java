package cn.thinkingdata.android.oaid;

import android.content.Context;


import com.bun.miitmdid.core.MdidSdkHelper;
import com.bun.miitmdid.interfaces.IIdentifierListener;
import com.bun.miitmdid.interfaces.IdSupplier;

import cn.thinkingdata.android.persistence.StorageOAID;

public class OaidHelper {

    public void loadOaid(Context context, StorageOAID storageOAID) {
        MdidSdkHelper.InitSdk(context, true, new IIdentifierListener() {
            @Override
            public void OnSupport(boolean b, final IdSupplier idSupplier) {
                if (idSupplier != null && idSupplier.isSupported()) {
                    storageOAID.put(idSupplier.getOAID());
                }
            }
        });
    }
}
