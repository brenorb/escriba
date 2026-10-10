package dev.brenorb.escriba;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.ParcelFileDescriptor;import java.io.*;
/** Debug-only fixture ingress. Absent from the release APK. */
public class FixtureProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(!"r".equals(mode)||name==null||!name.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9]+"))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),name),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri){return "audio/ogg";}
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
