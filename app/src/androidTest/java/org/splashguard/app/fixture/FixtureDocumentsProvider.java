package org.splashguard.app.fixture;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Only in the test APK. Supplies public synthetic fixtures through the real Android picker. */
public final class FixtureDocumentsProvider extends DocumentsProvider {
    public static final String ROOT_TITLE="SplashGuard Test Files";
    private static final String ROOT_ID="fixtures";
    private static final String[] ROOT_COLUMNS={Root.COLUMN_ROOT_ID,Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,Root.COLUMN_SUMMARY,Root.COLUMN_FLAGS,Root.COLUMN_ICON,Root.COLUMN_MIME_TYPES};
    private static final String[] DOCUMENT_COLUMNS={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED};
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){
        MatrixCursor result=new MatrixCursor(projection==null?ROOT_COLUMNS:projection);
        MatrixCursor.RowBuilder row=result.newRow();
        row.add(Root.COLUMN_ROOT_ID,ROOT_ID).add(Root.COLUMN_DOCUMENT_ID,ROOT_ID)
                .add(Root.COLUMN_TITLE,ROOT_TITLE).add(Root.COLUMN_SUMMARY,"Public offline test data")
                .add(Root.COLUMN_FLAGS,Root.FLAG_LOCAL_ONLY).add(Root.COLUMN_ICON,android.R.drawable.ic_menu_save)
                .add(Root.COLUMN_MIME_TYPES,"application/octet-stream");
        return result;
    }
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{
        MatrixCursor result=new MatrixCursor(projection==null?DOCUMENT_COLUMNS:projection);include(result,id);return result;
    }
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sortOrder)throws FileNotFoundException{
        if(!ROOT_ID.equals(parent))throw new FileNotFoundException("Unknown synthetic parent");
        MatrixCursor result=new MatrixCursor(projection==null?DOCUMENT_COLUMNS:projection);
        include(result,"valid");include(result,"tampered");return result;
    }
    @Override public boolean isChildDocument(String parent,String child){
        return ROOT_ID.equals(parent)&&("valid".equals(child)||"tampered".equals(child));
    }
    private void include(MatrixCursor cursor,String id)throws FileNotFoundException{
        boolean root=ROOT_ID.equals(id);if(!root&&!"valid".equals(id)&&!"tampered".equals(id))throw new FileNotFoundException("Unknown synthetic fixture");
        MatrixCursor.RowBuilder row=cursor.newRow();row.add(Document.COLUMN_DOCUMENT_ID,id)
                .add(Document.COLUMN_DISPLAY_NAME,root?ROOT_TITLE:("valid".equals(id)?"SG-valid.jws":"SG-tampered.jws"))
                .add(Document.COLUMN_MIME_TYPE,root?Document.MIME_TYPE_DIR:"application/octet-stream")
                .add(Document.COLUMN_FLAGS,0).add(Document.COLUMN_SIZE,root?0:1024)
                .add(Document.COLUMN_LAST_MODIFIED,0);
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        if(!"r".equals(mode)||(!"valid".equals(id)&&!"tampered".equals(id)))throw new FileNotFoundException("Read-only synthetic fixture");
        try{
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream input=getContext().getAssets().open("community-v1.jws")){
                byte[] buffer=new byte[2048];int n;while((n=input.read(buffer))!=-1){if(n==0||data.size()+n>65536)throw new IllegalStateException();data.write(buffer,0,n);}
            }
            String content=new String(data.toByteArray(),StandardCharsets.US_ASCII);
            if("tampered".equals(id)){int first=content.indexOf('.'),second=content.indexOf('.',first+1);int change=second+1;
                content=content.substring(0,change)+(content.charAt(change)=='A'?'B':'A')+content.substring(change+1);}
            File file=new File(getContext().getCacheDir(),"fixture-"+id+".jws");
            try(FileOutputStream output=new FileOutputStream(file)){output.write(content.getBytes(StandardCharsets.US_ASCII));}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(Exception unavailable){throw new FileNotFoundException("Synthetic fixture unavailable");}
    }
}
