package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // TODO: what if args is empty?
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                Repository.initCommand();
                break;
            case "add":
                Repository.add(args[1]);
                break;
            case "commit":
                //validateNumArgs("commit", args, 2);
                Repository.toCommit(args[1]);
                break;
            case "rm":
                validateNumArgs("rm", args, 2);
                Repository.rm(args[1]);
                break;

            case "rm-branch":
                Repository.rmBranch(args[1]);
                break;

            case "reset":
                if (args[1].matches("[a-z0-9]*")) {
                    Repository.reset(args[1]);
                    break;
            }

            case "log":
                Repository.log();
                break;
            case "checkout":
                if(args[1].equals("--")) {
                    validateNumArgs("checkout", args, 3);
                    Repository.checkoutFile(args[2]);
                    break;
                } else if (args[1].matches("[a-z0-9]*") && args[1].length() == 40) {
                    validateNumArgs("checkout", args, 4);
                    if (args[2].equals("--")) {
                        Repository.checkoutCommitAndFile(args[1], args[3]);
                        break;
                    } else {
                        System.out.println("Incorrect operands.");
                    }

                } else if (args[1] instanceof String) {
                    Repository.checkoutBranch(args[1]);
                    break;
                }
                break;
            case "find":
                Repository.find(args[1]);
                break;
            case "global-log":
                Repository.globalLog();
                break;
            case "status":
                Repository.status();
                break;
            case "branch":
                Repository.branch(args[1]);
                break;
            case "merge":
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");

        }
    }
    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            throw new RuntimeException(
                    String.format("Invalid number of arguments for: %s.", cmd));
        }
    }
}
