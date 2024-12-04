package gitlet;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;

public class Hash implements Serializable {

    public static final File STAGED_FILES = Utils.join(Repository.GITLET_DIR, "STAGED FILES");
    private HashMap<String, String> stagedFiles;
    private LinkedList<String> stagedForRemoval;
    private HashMap<String, LinkedList<String>> msgToCommits;

    public Hash() {
        stagedFiles = new HashMap<>();
        stagedForRemoval = new LinkedList<>();
    }

    public Hash(String message, String commitID) {
        msgToCommits = new HashMap<>();
        LinkedList<String> first = new LinkedList<>();
        first.add(commitID);
        msgToCommits.put(message, first);
    }


    public static void saveStagedFiles(Hash hash) {
        File g = Utils.join(STAGED_FILES, "files to stage");
        Utils.writeObject(g, hash);
    }

    public static Hash fromFileHash() {
        File pathToHash = Utils.join(STAGED_FILES, "files to stage"); //path to commit
        return Utils.readObject(pathToHash, Hash.class);
    }

    public static Hash fromFileHashMessages() {
        File pathToMessages = Utils.join(STAGED_FILES, "messages to commits");
        return Utils.readObject(pathToMessages,Hash.class);
    }

    public static void saveHashMessages(Hash hash) {
        File g = Utils.join(STAGED_FILES, "messages to commits");
        Utils.writeObject(g, hash);
    }

    public HashMap<String, String> getStagedFiles() {
        return this.stagedFiles;
    }

    public LinkedList<String> getStagedForRemoval() {
        return this.stagedForRemoval;
    }

    public HashMap<String, LinkedList<String>> getMsgToCommits() {
        return msgToCommits;
    }

    public static void clear() {
        Hash h = new Hash();
        saveStagedFiles(h);
    }



}
