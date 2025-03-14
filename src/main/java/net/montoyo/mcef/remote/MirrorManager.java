package net.montoyo.mcef.remote;

import net.montoyo.mcef.MCEF;
import net.montoyo.mcef.utilities.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * MirrorManager keeps track of valid & broken mirrors that should be used to download MCEF resources.
 * It also makes sure only HTTPS mirrors are used, depending on the user's choices.
 *
 * @author montoyo
 */
public class MirrorManager {
//    private static String getSelfhost() {
//        String text = new File("").getAbsolutePath().replace("\\", "/");
//        if (text.endsWith("/")) text = text.substring(0, text.length() - 1);
//        return text + "/data";
//    }
    
    private static final Mirror[] defaultMirrors = new Mirror[] {
            new Mirror("ds58-mcef-mirror", "https://ds58-mcef-mirror.ewr1.vultrobjects.com", Mirror.FLAG_SECURE),
//            new Mirror("montoyo_ancient", "https://montoyo.net/jcef/", Mirror.FLAG_SECURE),
//            new Mirror("self", "file://" + getSelfhost(), Mirror.FLAG_SECURE),
    };

    /**
     * The unique instance of the MirrorManager
     */
    public static final MirrorManager INSTANCE = new MirrorManager();

    private final ArrayList<Mirror> mirrors = new ArrayList<>();
    private final Random r = new Random();
    private Mirror current;

    private MirrorManager() {
        markCurrentMirrorAsBroken();
    }

    private void reset() {
        mirrors.clear();

        if(MCEF.FORCE_MIRROR != null)
            mirrors.add(new Mirror("user-forced", MCEF.FORCE_MIRROR, Mirror.FLAG_FORCED));
        else {
            ArrayList<Mirror> lst = new ArrayList<>(Arrays.asList(defaultMirrors));

            //Begin by adding all HTTPS mirrors in a random fashion
            while(!lst.isEmpty()) {
                Mirror m = lst.remove(r.nextInt(lst.size()));

                if(m.isSecure())
                    mirrors.add(m);
            }

            if(!MCEF.SECURE_MIRRORS_ONLY) {
                lst.addAll(Arrays.asList(defaultMirrors));

                //Then add all non-secure mirrors, if user didn't disable them
                while(!lst.isEmpty()) {
                    Mirror m = lst.remove(r.nextInt(lst.size()));

                    if(!m.isSecure())
                        mirrors.add(m);
                }
            }
        }
    }

    /**
     * @return The active mirror to be used
     */
    public Mirror getCurrent() {
        return current;
    }

    /**
     * Marks the active mirror as broken and chooses another one
     *
     * @return false if all mirrors were tested and the list was reset
     */
    public boolean markCurrentMirrorAsBroken() {
        boolean ret = true;

        if(mirrors.isEmpty()) {
            reset();
            ret = false;
        }

        current = mirrors.remove(0);
        Log.info(current.getInformationString());
        return ret;
    }

}
