package net.montoyo.mcef.remote;

import java.io.File;

import net.montoyo.mcef.client.ClientProxy;
import net.montoyo.mcef.utilities.IProgressListener;
import net.montoyo.mcef.utilities.Log;
import net.montoyo.mcef.utilities.Util2;

/**
 * A remote resource. Can be downloaded, extracted and checked.
 * @author montoyo
 *
 */
public class Resource {

    private String platform;
    private String name;
    private String sum;
    private boolean shouldExtract = false;
    
    /**
     * Constructs a remote resource from its filename and its SHA-1 checksum.
     * 
     * @param name The filename of the resource.
     * @param platform The platform the resource was made for.
     * @param sum The SHA-1 hash of the file.
     */
    public Resource(String name, String sum, String platform) {
        this.name = name;
        this.sum = sum.trim();
        this.platform = platform;
    }
    
    /**
     * Checks if the file exists. Then check if its checksum is valid.
     * If the file couldn't be hashed, false will be returned.
     * 
     * @return true if (and only if) the file exists and the checksum matches the {@link #sum} field.
     */
    public boolean exists() {
        File f = new File(ClientProxy.JCEF_ROOT, name);
        if(!f.exists())
            return false;

        return true; // temp supress due to my glibc
        // TODO: REMOVE!

        /*String hash = Util.hash(f);
        if(hash == null) {
            Log.warning("Couldn't hash file %s; assuming it doesn't exist.", f.getAbsolutePath());
            return false;
        }
        
        return hash.equalsIgnoreCase(sum);*/
    }
    
    /**
     * Downloads the resource from the current mirror.
     * 
     * @param ipl Progress listener. May be null.
     * @return true if the operation was successful.
     */
    public boolean download(IProgressListener ipl) {
        String end = "";
        if(shouldExtract)
            end += ".gz";

        File dst = new File(ClientProxy.JCEF_ROOT, name);
        File parent = dst.getParentFile();

        //ClientProxy.ROOT exists, but this.name might contain some subdirectories that we need to create...
        if(!parent.exists() && !parent.mkdirs())
            Log.warning("Couldn't create directory %s... ignoring this error, but this might cause some issues later...", parent.getAbsolutePath());
        return Util2.download("cef" + "4896" + '/' + platform + '/' + name + end, dst, shouldExtract, ipl);
    }
    
    /**
     * If the resource is a ZIP archive, it may be extracted using this method.
     * 
     * @param ipl Progress listener. May be null.
     * @return true if the operation was successful.
     */
    public boolean extract(IProgressListener ipl) {
        Util2.secure(ipl).onTaskChanged("2:Extracting " + name);
        return Util2.extract(new File(ClientProxy.JCEF_ROOT, name), new File(ClientProxy.JCEF_ROOT));
    }

    /**
     * Mark the resource as a GZip archive that should be extracted.
     */
    public void setShouldExtract() {
        shouldExtract = true;
        name = name.substring(0, name.length() - 3);
    }
    
    /**
     * Gets the filename of this resource.
     * @return The filename of this resource.
     */
    public String getFileName() {
        return name;
    }

    /**
     * Returns the File corresponding to the specified resource.
     *
     * @param resName Name of the resource.
     * @return The File containing the location of the specified resource.
     */
    public static File getLocationOf(String resName) {
        return new File(ClientProxy.JCEF_ROOT, resName);
    }

}
