package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.HashMap;

public class Staging implements Serializable {
    /**
     * BLOBS directory -- The saved contents of files.
     * Since Gitlet saves many versions of files, a single file might correspond
     * to multiple blobs: each being tracked in a different commit.
     */
    static final File BLOBS = Utils.join(Repository.GITLET_DIR, "blobs");


    public static void stageForAddition(String fileName) {

        List<String> filesInCWD = Utils.plainFilenamesIn(Repository.CWD); //files in the CWD
         if (!filesInCWD.contains(fileName)) {
             System.out.println("File does not exist.");
             return;
         }

        Commit currCommit = Repository.readHead(); //getting the current commit (HEAD)
        HashMap<String,String> filesCommitted = currCommit.getFilesInCommit(); //files in the current commit
        File fileCWD = getFileFromCWD(fileName); //path to file in CWD
        String s = Utils.readContentsAsString(fileCWD); //actual contents
        String fileSha1ID = computeCWDfileSHA1ID(fileName); //sha1ID of file in CWD
        Hash h = readHash(); //same as Hash.fromFileHash
        HashMap<String, String> stagedFiles = h.getStagedFiles();
        List<String> stagedForRemoval = h.getStagedForRemoval();

        /**
         * restores former contents.  Should simply "unremove" the file without staging.
         */

        if (stagedForRemoval.contains(fileName)) {
            stagedForRemoval.remove(fileName);
            Hash.saveStagedFiles((Hash) h);
            return;
        }




        if (filesCommitted.containsKey(fileName)) { //if the current commit contains the file, check if their contents are the same
            String fileSha1IDCommit = filesCommitted.get(fileName);
            if (fileSha1IDCommit.equals(fileSha1ID)) {
                if (stagedFiles.containsKey(fileName)) {
                    stagedFiles.remove(fileName);
                    Hash.saveStagedFiles((Hash) h);
                    saveBlob(fileSha1ID, fileName, s); //writing the file into blobs
                    return;
                }
                return;
            }
        }



        if (stagedFiles.containsKey(fileName)) { //stagedFiles contains the fileName
            if (!stagedFiles.get(fileName).equals(fileSha1ID)) { //if contents are different, replace
                stagedFiles.replace(fileName, fileSha1ID);
            } else {
                return;
            }
        } else {
            stagedFiles.put(fileName, fileSha1ID); //file was not found, add it
        }


        /**
         * compare the sha1ID of the file in the staging area and the file to be added to the staging area
         * to see if it needs to be updated, o/w return (file has not changed its contents)
         */
        Hash.saveStagedFiles((Hash) h);
        saveBlob(fileSha1ID, fileName, s); //writing the file into blobs
    }


    /**
     * BLOBS are being saved by their SHA1ID
     * @param sha1ID
     * @param
     */
    public static void saveBlob(String sha1ID, String fileName, String contents) {

        File s = new File(sha1ID);
        File path = Utils.join(BLOBS, sha1ID); //creating a new file in blobs that contain its sha1ID as the path

        Utils.writeObject(path, s);
        Utils.writeContents(path, contents);

    }

    public static File getFileFromCWD(String fileName) {
        return Utils.join(Repository.CWD, fileName);
    }

    public static void toRemove(String file) {
        Hash h = readHash();
        Commit c = Repository.readHead(); //current commit
        LinkedList<String> filesToRemove = h.getStagedForRemoval(); //files to remove
        HashMap<String, String> filesInCommit = c.getFilesInCommit(); //files in the commit
        HashMap<String, String> stagedFiles = h.getStagedFiles(); //files to add
        List<String> filesCWD = Utils.plainFilenamesIn(Repository.CWD);

        //failure case
        if (!stagedFiles.containsKey(file)) {
            if (!filesInCommit.containsKey(file)) {
                System.out.println("No reason to remove the file.");
                return;
            }
        }


        File path = Utils.join(Repository.CWD, file); //path to the file in the CWD

        //looking into staged files first, delete accordingly
        if (stagedFiles.containsKey(file)) {
            stagedFiles.remove(file);
        }

        //current commit files, stage for removal accordingly
        if (filesInCommit.containsKey(file)) {
            filesToRemove.add(file);
            if (filesCWD.contains(file)) {
                Utils.restrictedDelete(path);
            }
        }

        //sending the hash object to save changes
        Hash.saveStagedFiles(h);
    }


    /**
     * resetting the staging area by clearing the stored files
     */
    public static void clearStagingArea() {
        Hash.clear();
    }

    // Key: fileName ---> value: sha1ID
    public static void stageForCommitFiles(String fileName, String sha1ID) {
        Hash h = readHash();
        HashMap<String, String> stagedFiles = h.getStagedFiles();
        stagedFiles.put(fileName, sha1ID);
    }
    public static String getBlobSha1(File f) {
        byte[] byteRep = Utils.serialize(f);
        String sha1ID = Utils.sha1( byteRep);
        return sha1ID;
    }

    /**
     * reading contents of a file from the BLOBS directory
     * @param fileIdentifier
     * @return
     */
    public static byte[] fromFileBlob(String fileIdentifier) {
        File f = Utils.join(BLOBS, fileIdentifier);
        byte[] c = Utils.readContents(f);
        return c;
    }

    public static String getFileSha1ID(byte[] fileInf) {
        String fileSHA1ID = Utils.sha1(fileInf);
        return fileSHA1ID;
    }

    /**
     * reading contents of the provided file from the CWD
     * @param fileName
     * @return
     */
    public static byte[] fromFileCWD(String fileName) {
        File f = Utils.join(Repository.CWD, fileName);
        byte[] d = Utils.readContents(f);
        return d;
    }

    public static String computeCWDfileSHA1ID(String fileName) {
        byte[] contents = fromFileCWD(fileName); //reading the contents of the file as a byte array to compute its sha1ID
        return getFileSha1ID(contents);
    }

    public static Hash readHash() {
        Hash h = Hash.fromFileHash();
        return h;
    }

    public static LinkedList<String> getStagedForRemoval() {
        Hash h = readHash();
        LinkedList<String> stagedForRemoval = h.getStagedForRemoval();
        return stagedForRemoval;
    }
}
