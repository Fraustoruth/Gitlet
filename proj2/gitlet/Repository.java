package gitlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/** Represents a gitlet repository.
 *  @author Ruth Frausto <3
 */
public class Repository {


    public static final File CWD = new File(System.getProperty("user.dir"));
    static final File GITLET_DIR = Utils.join(CWD, ".gitlet");
    static File INITCOMMIT = Utils.join(GITLET_DIR, "initial commit");
    static File BRANCHES = Utils.join(GITLET_DIR, "branches");
    static File CURRENT_BRANCH = Utils.join(BRANCHES, "current branch");

    /**
     * commitMapping will have key: childID ---> value: parentID to account for branching
     */

    private static Commit HEAD = null;
    private static Commit master = null;
    private static HashMap<String, LinkedList<String>> messagesToCommits;
    private static String currentBranch;


    /**
     * .gitlet/ -- top level folder for all persistent data
     *      -commits/ -- folder to store the commits
     *              -initialCommit --file containing the initial commit
     *      -blobs/ --folder to store all the file objects
     */
    public static void initCommand() {

        GITLET_DIR.mkdir();
        Commit.COMMITS.mkdir();
        Staging.BLOBS.mkdir();
        Hash.STAGED_FILES.mkdir();
        BRANCHES.mkdir();
        CURRENT_BRANCH.mkdir();

        try {
            INITCOMMIT.createNewFile();
        } catch (IOException exc) {
            System.out.println("A Gitlet version-control system already exists in the current directory");
            return;
        }

        Hash h = new Hash();
        Commit initialCommit = new Commit(h);
        Utils.writeObject(INITCOMMIT, initialCommit);
        String message = initialCommit.getMessage();//computing the commit's sha1ID
        Hash msgToCommit = new Hash(message, initialCommit.getSha1ID()); //creating a hashMap object to store msg and commitID
        Hash.saveHashMessages(msgToCommit);
        /**
         * save init commit will require the directory, and the initial commit object
         */
        Hash.saveStagedFiles(h);
        Commit.saveCommit(initialCommit);
        saveBranch(initialCommit.getSha1ID(), "master"); //path to commit in the COMMITS dir using sha1ID
        setCurrentBranchName("master"); //saving just the name of branch as string
        updateHead(initialCommit); //will also assign a pointer to the commit with the name of the current branch
    }


    /**
     * retrieve the files currently staged and create a commit object
     * @param message
     */
    public static void toCommit(String message) {
        if (message.isEmpty()) {
            System.out.println("Please enter a commit message");
            return;
        }

        Hash h = Hash.fromFileHash();
        HashMap<String, String> filesToCommit = h.getStagedFiles(); //files staged for addition
        LinkedList<String> filesToRemove = h.getStagedForRemoval(); //files staged for removal

        if (filesToCommit.isEmpty()) {
            if (filesToRemove.isEmpty()) {
                System.out.println("No changes added to the commit.");
                return;
            }
        }

        String parentID = readHead().getSha1ID();
        Commit currCommit = new Commit(message, parentID, filesToCommit, filesToRemove);
        Commit.saveCommit(currCommit);
        Hash msgHash = readHashMessages();
        messagesToCommits = msgHash.getMsgToCommits();
        putMessageAndCommit(currCommit.getMessage(), currCommit.getSha1ID(), messagesToCommits);
        saveMessagesToCommit(msgHash);
        updateHead(currCommit);
        saveBranch(currCommit.getSha1ID(), getCurrentBranchName());
        updateCurrentBranch(currCommit.getSha1ID());
        Staging.clearStagingArea();
        return;

    }

    public static void merge(String branch) {
        HashMap<String, String > stagedFiles = Hash.fromFileHash().getStagedFiles();
        LinkedList<String> stagedForRemoval = Hash.fromFileHash().getStagedForRemoval();
        if (!stagedFiles.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }

        List<String> branches = Utils.plainFilenamesIn(BRANCHES);
        if (!branches.contains(branch)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        if (getCurrentBranchName().equals(branch)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }


        List<String> filesCWD = Utils.plainFilenamesIn(CWD); //if a file might get lost
        HashMap<String, String> currCommitFiles = readHead().getFilesInCommit();
        for (String file : filesCWD) {
            if (!currCommitFiles.containsKey(file)) {
                if (!stagedFiles.containsKey(file)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    return;
                }
            }
        }

        //get split point; where both branches have the same parent sha1ID
        String currBranchName = getCurrentBranchName(); //current branch

        Commit a = getSpecifiedBranch(currBranchName); //wont be the same after the while loop
        Commit b = getSpecifiedBranch(branch); //wont be the same after the while loop
        Commit currentBranch = a;
        Commit specifiedBranch = b;
        Commit splitPoint = null;

        //iterate until both parent's sha1ID are the same to find the common commit (split point)
        while (a.getParentSha1ID() != null || b.getParentSha1ID() != null) {
            if (a.getParentSha1ID() == null) {
                if (b.getParentSha1ID() == null) { //case 1 - tree commits with 3 commits; 1 parent and 2 children TODO: this might be redundant bc of the while condition
                    System.out.println("Given branch is an ancestor of the current branch.");
                    return;
                } else if (b.getParentSha1ID().equals(a.getSha1ID())) { //case 2 - next to each other
                    System.out.println("Given branch is an ancestor of the current branch.");
                    return;
                } else { //case 4 - not next to each other
                    b = Commit.fromFile(b.getParentSha1ID());
                }
            } else if (b.getParentSha1ID() == null) {
                if (a.getParentSha1ID().equals(b.getSha1ID())) { //case 2
                    System.out.println("Given branch is an ancestor of the current branch.");
                    return;
                } else { //case 3
                    a = Commit.fromFile(a.getParentSha1ID());
                }
            } else if (a.getParentSha1ID().equals(b.getSha1ID())) {
                splitPoint = Commit.fromFile(a.getSha1ID());
                checkoutBranch(currBranchName);
                System.out.println("Current branch fast-forwarded.");
                return;
            } else if (b.getParentSha1ID().equals(a.getSha1ID())) {
                splitPoint = Commit.fromFile(b.getSha1ID()); //not actually split point, change it
                checkoutBranch(branch);
                System.out.println("Current branch fast-forwarded.");

                return;
            } else {
                    a = Commit.fromFile(a.getParentSha1ID());
                    b = Commit.fromFile(b.getParentSha1ID());
            }
        }


        if (splitPoint != null) {
            HashMap<String, String> filesSplitPoint = splitPoint.getFilesInCommit();//files in split point
            HashMap<String, String> filesInCurrentBranch = currentBranch.getFilesInCommit();
            HashMap<String, String> filesInBranch = specifiedBranch.getFilesInCommit();
            //case 1: files modified in branch but not in current branch -- stage them for commit
            for (String file : filesSplitPoint.keySet()) {
                if (filesInBranch.containsKey(file)) {
                    if (!filesSplitPoint.get(file).equals(filesInBranch.get(file))) { //contents of branch have been modified since split point
                        if (filesInCurrentBranch.containsKey(file)) {
                            if (filesInCurrentBranch.get(file).equals(filesSplitPoint.get(file))) { //contents in current branch were not modified since split point
                             stagedFiles.put(file, filesInBranch.get(file));
                            }
                        }
                    }
                }
            }

            //case 2 : files modified in current branch but not in given branch -- do nothing
            for (String file : filesSplitPoint.keySet()) {
                if (filesInCurrentBranch.containsKey(file)) {
                    if (filesInCurrentBranch.get(file).equals(filesSplitPoint.get(file))) {
                        if (filesInBranch.containsKey(file)) {
                            if (!filesInBranch.get(file).equals(filesSplitPoint.get(file))) {
                                continue;
                            }
                        }
                    }
                }
            }

            //case 3
            for (String file : filesSplitPoint.keySet()) {
                if (filesInCurrentBranch.containsKey(file)) {
                    if (filesInBranch.containsKey(file)) {
                        if (filesInBranch.get(file).equals(filesInCurrentBranch.get(file))) {
                            continue;
                        }
                    }
                } else if (!filesInBranch.containsKey(file)) {
                    continue;
                }
            }

            //case 4
            /**
             * Any files that were not present at the split point
             * and are present only in the current branch should remain as they are.
             */

            for (String file : filesInCurrentBranch.keySet()) {
                if (!filesSplitPoint.containsKey(file)) {
                    if (!filesInBranch.containsKey(file)) {
                        continue;
                    }
                }
            }

            /**
             * Any files that were not present at the split point
             * and are present only in the given branch should be checked out and staged.
             */
            //case 5
            for (String file : filesInBranch.keySet()) {
                if (!filesSplitPoint.containsKey(file)) {
                    if (!filesInCurrentBranch.containsKey(file)) {
                        checkoutCommitAndFile(specifiedBranch.getSha1ID(), file);
                    }
                }
            }

            /**
             * Any files present at the split point, unmodified in the current branch,
             * and absent in the given branch should be removed (and untracked).
             */
            //case 6
            for (String file : filesSplitPoint.keySet()) {
                if (filesInCurrentBranch.containsKey(file)) {
                    if (filesInCurrentBranch.get(file).equals(filesSplitPoint.get(file))) {
                        if (!filesInBranch.containsKey(file)) {
                            stagedForRemoval.add(file);
                        }
                    }
                }
            }

            /**
             * Any files present at the split point, unmodified in the given branch,
             * and absent in the current branch should remain absent.
             */
            //case 7
            for (String file : filesSplitPoint.keySet()) {
                if (filesInBranch.containsKey(file)) {
                    if (filesInBranch.get(file).equals(filesSplitPoint.get(file))) {
                        if (!filesInCurrentBranch.containsKey(file)) {
                            continue;
                        }
                    }
                }
            }
            //TODO: case 8

            Commit merged = new Commit("Merged " + branch + " into " + currBranchName, currBranchName, branch, stagedFiles, stagedForRemoval);
            Commit.saveCommit(merged);
            Hash msgHash = readHashMessages();
            messagesToCommits = msgHash.getMsgToCommits();
            putMessageAndCommit(merged.getMessage(), merged.getSha1ID(), messagesToCommits);
            saveMessagesToCommit(msgHash);
            updateHead(merged);
            saveBranch(merged.getSha1ID(), getCurrentBranchName());
            updateCurrentBranch(merged.getSha1ID());
            Staging.clearStagingArea();
            return;
        }
    }

    public static void checkoutBranch(String branchName) {

        List<String> branches = Utils.plainFilenamesIn(BRANCHES); //to check if the branchName is valid
        if (!branches.contains(branchName)) {
            System.out.println("No such branch exists.");
            return;
        }

        if (branchName.equals(getCurrentBranchName())) {
            System.out.println("No need to checkout the current branch.");//if we're at the same branch
            return;
        }

        List<String> filesCWD = Utils.plainFilenamesIn(CWD); //if a file might get lost
        HashMap<String, String> stagedFiles = Hash.fromFileHash().getStagedFiles();
        HashMap<String, String> currCommitFiles = readHead().getFilesInCommit();
        for (String file : filesCWD) {
            if (!currCommitFiles.containsKey(file)) {
                if (!stagedFiles.containsKey(file)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    return;
                }
            }
        }

        Commit c = getSpecifiedBranch(branchName);
        HashMap<String, String> filesInBranch = c.getFilesInCommit();

        for (String cwdFile: filesCWD) {
            if (filesInBranch.containsKey(cwdFile)) {
                substituteFileContents(filesInBranch.get(cwdFile), cwdFile);
            } else {
                File f = Utils.join(CWD, cwdFile);
                Utils.restrictedDelete(f);
            }
        }

        filesCWD = Utils.plainFilenamesIn(CWD);
        for (String file : filesInBranch.keySet()) {
            if (!filesCWD.contains(file)) {
                writeContentsCWD(filesInBranch.get(file), file);
            }
        }

        updateHead(c);
        saveBranch(c.getSha1ID(), branchName);
        setCurrentBranchName(branchName);
        updateCurrentBranch(c.getSha1ID());
    }


    public static void branch(String newBranch) {
        List<String> branches = Utils.plainFilenamesIn(BRANCHES);
        if (branches.contains(newBranch)) {
            System.out.println("A branch with that name already exists.");
            return;
        }

        String currentCommitSha1ID = readHead().getSha1ID();
        saveBranch(currentCommitSha1ID, newBranch);

    }

    public static void rmBranch(String branchName) {
        List<String> branches = Utils.plainFilenamesIn(BRANCHES);
        if (!branches.contains(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (getCurrentBranchName().equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        File file = Utils.join(BRANCHES, branchName);
        file.delete(); //TODO: bug; throwing an illegal error
    }

    //will contain the sha1ID of the specified commit
    //in order to retrieve the specified branch, will need to read the contents of the file
    private static void saveBranch(String sha1ID, String branchName) {
        File newBranch = new File(sha1ID);
        File path = Utils.join(BRANCHES, branchName);

        //Utils.writeObject(path, newBranch);
        Utils.writeContents(path, sha1ID);
    }

    private static Commit getSpecifiedBranch(String branchName) {
        return fromFileBranch(branchName);
    }

    private static Commit fromFileBranch(String fileIdentifier) {
        File pathToCommit = Utils.join(BRANCHES, fileIdentifier); //path to commit
        String sha1IDofCommit = Utils.readContentsAsString(pathToCommit);
        Commit c = Commit.fromFile(sha1IDofCommit);
        return c;
    }

    //branch name only
    private static void setCurrentBranchName(String branchName) {
        //setting the current branch string variable to branchName
        File currBranchPath = Utils.join(CURRENT_BRANCH, "current branch");
        Utils.writeContents(currBranchPath, branchName);
    }

    private static String getCurrentBranchName() {
        File pathToBranch = Utils.join(CURRENT_BRANCH, "current branch");
        currentBranch = Utils.readContentsAsString(pathToBranch);
        return currentBranch;
    }

    private static void updateCurrentBranch(String commitID) {
        saveBranch(commitID, getCurrentBranchName());
    }

    /**
     * Displays what branches currently exist, and marks the current branch with a *.
     * Also displays what files have been staged for addition or removal.
     */
    public static void status() {

        //printing branches first
        List<String> branches = Utils.plainFilenamesIn(BRANCHES);
        String currentBranch = getCurrentBranchName();
        printStatusHeader("Branches");
        printStatus("*" + currentBranch);
        for (String branch : branches) {
            if (!branch.equals(currentBranch)) {
                printStatus(branch);
            }
        }
        System.out.println();

        //printing files staged for addition
        Hash h = Hash.fromFileHash();
        HashMap<String, String> stagedFiles = h.getStagedFiles();
        printStatusHeader("Staged Files");
        if (!stagedFiles.isEmpty()) {
            for (String a : stagedFiles.keySet()) {
                printStatus(a);
            }
        }
        System.out.println();

        //printing files staged for removal
        List<String> removed = h.getStagedForRemoval();
        printStatusHeader("Removed Files");
        for (String r : removed) {
            printStatus(r);
        }
        System.out.println();

        List<String> cwd = Utils.plainFilenamesIn(CWD);
        HashMap<String, String> filesInCommit = readHead().getFilesInCommit();
        List<String> filesChanged = new LinkedList<>();

        //CASE 1
        //comparing sha1ID of files in CWD and commit, if different add to filesChanged and have not been staged for addition
        //first filter
        for (String fileCWD : cwd) {
            if (filesInCommit.containsKey(fileCWD)) {
                //get contents of file in CWD and compute sha1ID
                String sha1IDCWD = Staging.computeCWDfileSHA1ID(fileCWD);
                if (!filesInCommit.get(fileCWD).equals(sha1IDCWD)) {
                    if (!stagedFiles.containsKey(fileCWD)) {
                        filesChanged.add(fileCWD + " (modified)");
                    }
                }
            }
        }

        //CASE 2 check all files in staged for addition to see if their contents in the CWD is the same, if not, add it tp filesChanged
        for (String staged : stagedFiles.keySet()) {
            if (cwd.contains(staged)) {
                String sha1IDCWD = Staging.computeCWDfileSHA1ID(staged);
                if (!stagedFiles.get(staged).equals(sha1IDCWD)) {
                    filesChanged.add(staged + " (modified)");
                }
            }
        }

        //CASE 3 present in staged files but not in CWD
        for (String staged : stagedFiles.keySet()) {
            if (!cwd.contains(staged)) {
                filesChanged.add(staged + " (deleted)");
            }
        }

        //CASE 4 Not staged for removal, but tracked in the current commit and deleted from the working directory.

        for (String committed : filesInCommit.keySet()) {
            if (!removed.contains(committed)) {
                if (!cwd.contains(committed)) {
                    filesChanged.add(committed + " (deleted)");
                }
            }
        }

        printStatusHeader("Modifications Not Staged For Commit");
        for (String file : filesChanged) {
            printStatus(file);
        }
        System.out.println();

        //printing untracked files
        //has to be in the CWD but not in the current commit nor staged files

        //files staged to add - applying first filter
        List<String> untracked = new LinkedList<>();
        for (String c : cwd) {
            if (!stagedFiles.containsKey(c)) {
                if (!filesInCommit.containsKey(c)) {
                    untracked.add(c);
                }

            }
        }

        printStatusHeader("Untracked Files");
        for (String file : untracked) {
            printStatus(file);
        }
        System.out.println();

    }

    private static void printStatus(String name) {
        System.out.println(name);
    }

    private static void printStatusHeader(String objectName) {
        System.out.println("=== " + objectName + " ===");

    }

    public static void globalLog() {
        List<String> filesInCommit = Utils.plainFilenamesIn(Commit.COMMITS);
        for (String s : filesInCommit) {
            Commit c = Commit.fromFile(s);
            logPrinter(c);
        }
    }

    public static void find(String message) {
        Hash msgHash = readHashMessages();
        messagesToCommits = msgHash.getMsgToCommits();
        if (!messagesToCommits.containsKey(message)) {
            System.out.println("Found no commit with that message.");
            return;
        }
        LinkedList<String> commitIDS = messagesToCommits.get(message);
        for (String commitID : commitIDS) {
            System.out.println(commitID);
        }
    }

    private static Hash readHashMessages() {
        return Hash.fromFileHashMessages();
    }

    private static void putMessageAndCommit(String message, String sha1ID, HashMap<String, LinkedList<String>> messagesToCommits) {
        if (messagesToCommits.containsKey(message)) {
            LinkedList<String> commitIDS = messagesToCommits.get(message);
            commitIDS.add(sha1ID);
            return;
        }
        LinkedList<String> newCommitsID = new LinkedList<>();
        newCommitsID.add(sha1ID);
        messagesToCommits.put(message, newCommitsID);
        return;
    }

    private static void saveMessagesToCommit(Hash hash) {
        Hash.saveHashMessages(hash);
    }

    private static void updateHead(Commit currCommit) {
        File f = Utils.join(Commit.COMMITS, "HEAD");
        Utils.writeObject(f, currCommit);
    }

    public static Commit readHead() {
        Commit c = Commit.fromFile("HEAD");
        return c;
    }


    /**
     * starts the unstaging process
     * @param file
     */
    public static void rm(String file) {
        Staging.toRemove(file);
    }

    public static void log() {
        /**
         * HEAD points at the current commit, I need to get the actual object print its metadata
         * and get its parent's commit and so on until parentID is NULL
         */

        Commit headCommit = readHead();
        logPrinter(headCommit);
        while (headCommit.getParentID() != null) {
            Commit c = Commit.fromFile(headCommit.getParentID());
            logPrinter(c);
            headCommit = c;
        }
    }

    private static void logPrinter(Commit c) {

        String[] s = c.getTimeStamp().toString().split(" ");
        System.out.println("===");
        System.out.println("commit " + c.getSha1ID());
        System.out.println("Date: " + s[0] + " " + s[1] + " " + s[2] + " " +  s[3] + " " + s[5] + " -0800");
        System.out.println(c.getMessage());
        System.out.println();

    }

    public static void checkoutFile(String file) {
        /**
         * retrieving file from the staging area and see if the files in the CWD have been staged,
         * if not, exit (dangerous command)
         */
        Commit headComm = readHead(); //read the commit object
        HashMap<String, String> filesInHeadCommit = headComm.getFilesInCommit(); //retrieve files from current commit - HEAD

        if (filesInHeadCommit.isEmpty()) {
            return;
        }

        if (!filesInHeadCommit.containsKey(file)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String replacement = filesInHeadCommit.get(file); //sha1 of file in commit
        substituteFileContents(replacement, file);

    }

    public static void reset(String sha1IDCommit) {
        //no commit with that id
        List<String> commitsIDS = Utils.plainFilenamesIn(Commit.COMMITS);
        if (!commitsIDS.contains(sha1IDCommit)) {
            System.out.println("No commit with that id exists.");
            return;
        }

        List<String> cwdFiles = Utils.plainFilenamesIn(CWD); //files in cwd
        Commit commitRequested = Commit.fromFile(sha1IDCommit); //Specified commit
        HashMap<String, String> filesRequested = commitRequested.getFilesInCommit(); //files in the commit to checkout

        HashMap<String, String> headCommitFiles = readHead().getFilesInCommit();
        HashMap<String, String> stagedFiles = Hash.fromFileHash().getStagedFiles();
        for (String file : cwdFiles) {
            if (!headCommitFiles.containsKey(file)) {
                if (!stagedFiles.containsKey(file)) {
                    System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                    return;
                }
            }
        }

        //remove files from cwd
        for (String fileCwd : cwdFiles) {
            File path = Utils.join(CWD, fileCwd);
            Utils.restrictedDelete(path);
        }

        for (String file :filesRequested.keySet()) {
            writeContentsCWD(filesRequested.get(file), file);
        }

        for (String s : stagedFiles.keySet()) {
            if (!filesRequested.containsKey(s)) {
                stagedFiles.remove(s);
            }
        }

        //update head pointer and current branch

        updateHead(commitRequested);
        saveBranch(sha1IDCommit, getCurrentBranchName());
        setCurrentBranchName(getCurrentBranchName());
        updateCurrentBranch(commitRequested.getSha1ID());

        //clear staging area
        Staging.clearStagingArea();
    }

    private static void writeContentsCWD(String fileSha1ID, String fileName) {

        File path = Utils.join(Staging.BLOBS, fileSha1ID);
        String conts = Utils.readContentsAsString(path);
        File pathCWD = Utils.join(CWD, fileName);

        Utils.writeObject(pathCWD, fileName);
        Utils.writeContents(pathCWD, conts);

    }

    private static void substituteFileContents(String replacementSHA1ID, String file) {

        //find the file and read contents
        File pathCWD = Utils.join(CWD, file);
        File path = Utils.join(Staging.BLOBS, replacementSHA1ID);
        String conts = Utils.readContentsAsString(path);

        Utils.writeObject(pathCWD, file);
        Utils.writeContents(pathCWD, conts);
    }

    public static void checkoutCommitAndFile(String commitID, String file) {
        List<String> commitIDS = Utils.plainFilenamesIn(Commit.COMMITS);
        if (!commitIDS.contains(commitID)) {
            System.out.println("No commit with that id exists.");
            return;
        }

        Commit c = Commit.fromFile(commitID);
        HashMap<String, String> filesInCommit = c.getFilesInCommit();
        if (!filesInCommit.containsKey(file)) {
            System.out.println("File does not exist in that commit.");
            return;
        }

        String replacement = filesInCommit.get(file);
        substituteFileContents(replacement, file);

    }

    public static void add(String fileName) {
        Staging.stageForAddition(fileName);
    }


}
