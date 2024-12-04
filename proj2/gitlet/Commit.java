package gitlet;
import java.io.File;
import java.io.Serializable;
import java.util.*;


/** Represents a gitlet commit object.
 *  does at a high level.
 *
 *  @author Ruth Frausto
 */
public class Commit implements Serializable {


    static final File COMMITS = Utils.join(Repository.GITLET_DIR, "commits");
    /** The message of this Commit. */
    private String message;
    private Date timeStamp;
    private String sha1ID;
    private String parentID;
    private String firstParent;
    private String secondParent;

    /**
     * instance variable
     * key: file name ----> value: sha1ID
     */
    private HashMap<String, String> filesInCommit;


    /**
     * constructor for initial commit
     */
    public Commit(Hash h) {
        this.message = "initial commit";
        this.timeStamp = new Date(0);
        this.parentID = null;
        this.filesInCommit = h.getStagedFiles();
    }

    public Commit(String message, String firstParent, String secondParent,
                  HashMap<String, String> filesToCommit, LinkedList<String> filesToRemove) {
        this.message = message;
        this.timeStamp = new Date();
        this.firstParent = firstParent;
        this.secondParent = secondParent;
        this.filesInCommit = checkParentFiles(firstParent, filesToCommit, filesToRemove);
    }

    public Commit(String message, String parentID, HashMap<String, String> filesToCommit,
                  LinkedList<String> filesToRemove) {
        this.message = message;
        this.timeStamp = new Date();
        this.parentID = parentID;
        this.filesInCommit = checkParentFiles(parentID, filesToCommit, filesToRemove);
    }

    public HashMap<String, String> getFilesInCommit() {
        return this.filesInCommit;
    }

    private HashMap<String, String> checkParentFiles(String parentCommitID,
                                                     HashMap<String, String> filesInCommit, LinkedList<String> filesToRemove) {
        Commit parentCommit = fromFile(parentCommitID); //getting the parent commit object

        if (!parentCommit.getFilesInCommit().isEmpty()) {
            //getting the files that were commited in the parent
            HashMap<String, String> parentFiles = parentCommit.getFilesInCommit();
            Set<String> keys = filesInCommit.keySet(); //getting the keys to the files staged for addition
            Set<String> parentKeys = parentFiles.keySet();
            for (String key: parentKeys) {
                if (filesInCommit.containsKey(key)) {
                    //comparing sha1ID of the same file name in parent and staged file
                    if (parentFiles.get(key).equals(filesInCommit.get(key))) {
                        continue; //do not update anything
                    }
                } else {
                    filesInCommit.put(key, parentFiles.get(key));
                }
            }
        }
        if (!filesToRemove.isEmpty()) {
            for (String s : filesToRemove) {
                if (filesInCommit.containsKey(s)) {
                    filesInCommit.remove(s);
                }
            }
        }


        return filesInCommit; //taking care of files staged for removal
    }

    /**
     * if files were staged for removal, this method will clean the cloned "parentFiles" hashmap and will only contain
     * the desired files in the new commit
     */

    public static HashMap<String, String> stageForRemoval(HashMap<String, String> filesInCommit) {
        LinkedList<String> toRemove = Staging.getStagedForRemoval();
        for (String s : toRemove) {
            if (filesInCommit.containsKey(s)) {
                filesInCommit.remove(s);
            }
        }
        return filesInCommit;
    }

    public String getMessage() {
        return this.message;
    }

    public Date getTimeStamp() {
        return this.timeStamp;
    }

    public String getParentID() {
        return this.parentID;
    }

    public String getSha1ID() {
        byte[] byteRep = Utils.serialize(this);
        String sha1ID = Utils.sha1( byteRep);
        return sha1ID;

    }


    /**
     * takes in a sha1ID and reads the object from the commits directory
     * @param fileIdentifier
     * @return
     */
    public static Commit fromFile(String fileIdentifier) {
        File pathToCommit = Utils.join(COMMITS, fileIdentifier); //path to commit
        Commit c = Utils.readObject(pathToCommit, Commit.class);
        return c;
    }
    /**
     * save commit in "commits" directory
     * @param obj
     */
    public static void saveCommit(Commit obj) {
        String sha1ID = obj.getSha1ID();
        //saving object in the commits directory
        File g = Utils.join(COMMITS, sha1ID);
        Utils.writeObject(g, obj);

    }

    public String getParentSha1ID() {
        return this.parentID;
    }


}
