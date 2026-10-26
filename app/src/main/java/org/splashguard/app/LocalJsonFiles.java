package org.splashguard.app;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Temporary SAF streams only; no persistent grants, shared-storage permission or network. */
final class LocalJsonFiles {
    private LocalJsonFiles(){}
    static String read(ContentResolver resolver,Uri uri)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
        try(InputStream input=resolver.openInputStream(uri)){if(input==null)throw new java.io.IOException();int n;
            while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();if(n==0||bytes.size()+n>OfflineRuleStore.MAX_PACK_BYTES)throw new java.io.IOException();bytes.write(buffer,0,n);}}
        OfflineRuleStore.requireInputSize(bytes.size());
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
    static void write(ContentResolver resolver,Uri uri,String text)throws Exception{
        if(text==null)throw new IllegalArgumentException();byte[] bytes=text.getBytes(StandardCharsets.UTF_8);OfflineRuleStore.requireInputSize(bytes.length);
        try(OutputStream output=resolver.openOutputStream(uri,"wt")){if(output==null)throw new java.io.IOException();output.write(bytes);}
    }
}
