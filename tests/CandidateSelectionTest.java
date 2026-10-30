import org.splashguard.app.CandidateSelection;

public final class CandidateSelectionTest {
    private static int passed;

    /** Models native node/window identity independently from spoofable metadata. */
    private static final class Node {
        final int window;
        final long nativeId;
        final String metadata;
        final long parentId;

        Node(int window, long nativeId, String metadata, long parentId) {
            this.window = window;
            this.nativeId = nativeId;
            this.metadata = metadata;
            this.parentId = parentId;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Node)) return false;
            Node node = (Node) other;
            return window == node.window && nativeId == node.nativeId;
        }

        @Override public int hashCode() {
            return 31 * window + (int) (nativeId ^ (nativeId >>> 32));
        }
    }

    private static void check(String name, boolean result) {
        if (!result) throw new AssertionError(name);
        passed++;
    }

    private static boolean rejectsNull(CandidateSelection<Node> selection) {
        try { selection.offer(null); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }

    public static void main(String[] args) {
        String sameMetadata = "splash_skip|Button|[900,80,1100,160]|跳过广告";
        Node real = new Node(1, 100, sameMetadata, 0);
        Node fake = new Node(1, 200, sameMetadata, 0);
        CandidateSelection<Node> empty = new CandidateSelection<>();
        check("empty has no unique target", empty.unique() == null);
        check("empty is not ambiguous", !empty.ambiguous());
        check("null is rejected", rejectsNull(empty));
        check("rejected null does not become a target", empty.unique() == null && !empty.ambiguous());

        CandidateSelection<Node> realFirst = new CandidateSelection<>();
        realFirst.offer(real);
        check("single target retains original handle", realFirst.unique() == real);
        check("single target is not ambiguous", !realFirst.ambiguous());
        realFirst.offer(fake);
        check("real then fake is ambiguous", realFirst.ambiguous());
        check("real then fake never picks first", realFirst.unique() == null);

        CandidateSelection<Node> fakeFirst = new CandidateSelection<>();
        fakeFirst.offer(fake);
        fakeFirst.offer(real);
        check("fake then real is ambiguous", fakeFirst.ambiguous());
        check("fake then real never picks first", fakeFirst.unique() == null);
        check("spoofed equal metadata still represents different nodes", real.metadata.equals(fake.metadata) && !real.equals(fake));

        CandidateSelection<Node> repeated = new CandidateSelection<>();
        repeated.offer(real);
        repeated.offer(real); // text and description may match on this same node.
        Node anotherHandle = new Node(1, 100, sameMetadata, 0);
        repeated.offer(anotherHandle);
        check("equivalent native handles are deduplicated", !repeated.ambiguous());
        check("deduplication retains first equivalent handle", repeated.unique() == real);
        repeated.offer(new Node(1, 100, "same node with updated countdown text", 0));
        check("metadata variation does not create another native node", repeated.unique() == real && !repeated.ambiguous());
        check("null after a valid node is rejected", rejectsNull(repeated));
        check("rejected null does not erase valid node", repeated.unique() == real && !repeated.ambiguous());

        realFirst.offer(real);
        realFirst.offer(anotherHandle);
        realFirst.offer(fake);
        check("old-node repeats cannot clear conflict", realFirst.ambiguous() && realFirst.unique() == null);
        check("null after conflict remains rejected", rejectsNull(realFirst));
        check("rejected null cannot clear conflict", realFirst.ambiguous() && realFirst.unique() == null);
        realFirst.offer(new Node(1, 300, sameMetadata, 0));
        check("third distinct candidate cannot restore uniqueness", realFirst.ambiguous() && realFirst.unique() == null);

        CandidateSelection<Node> parentAndChild = new CandidateSelection<>();
        Node parent = new Node(1, 400, sameMetadata, 0);
        Node child = new Node(1, 401, sameMetadata, parent.nativeId);
        parentAndChild.offer(parent);
        parentAndChild.offer(child);
        check("parent relationship alone does not prove same click target", child.parentId == parent.nativeId && parentAndChild.ambiguous());
        check("unproven parent-child pair yields no target", parentAndChild.unique() == null);

        CandidateSelection<Node> differentWindows = new CandidateSelection<>();
        differentWindows.offer(real);
        differentWindows.offer(new Node(2, 100, sameMetadata, 0));
        check("same node number in another window is distinct", differentWindows.ambiguous() && differentWindows.unique() == null);

        CandidateSelection<Node> freshScan = new CandidateSelection<>();
        freshScan.offer(real);
        check("new scan has its own identity selection", freshScan.unique() == real && !freshScan.ambiguous());
        System.out.println("CandidateSelection: " + passed + " checks passed");
    }
}
