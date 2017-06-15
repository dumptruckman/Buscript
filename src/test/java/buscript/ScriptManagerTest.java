package buscript;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class ScriptManagerTest {

    private ScriptManager sm;

    @Before
    public void setUp() throws Exception {
        sm = new ScriptManager(Files.createTempDirectory("scripts").toFile(),
                Logger.getLogger("ScriptManagerTest"));
        sm.addScriptMethods(new TestMethods(sm));
    }

    @Test
    public void testGlobals() {
        List testList = new ArrayList<>();
        sm.setScriptVariable("testList", testList);

        sm.runScript("testList.add(1);", null);
        assertEquals(1, testList.get(0));
        assertEquals(1, ((List) sm.getScriptVariable("testList")).get(0));

        testList.add(2);
        assertEquals(2, testList.get(1));
        assertEquals(2, ((List) sm.runScript("testList", null)).get(1));
    }

    @Test
    public void testGlobalMethods() {
        assertEquals(5, sm.runScript("add(2, 3)", null));
    }

    @Test
    public void testTargetedScript() {
        assertEquals("Test", sm.executeScript("var name = target; name", null, "Test"));
    }

    @Test
    public void testTargetReplacementScript() {
        assertEquals("Test", sm.executeScript(sm.stringReplace("testReplace()"), null, "Test"));
    }

    public static class TestMethods {
        ScriptManager sm;
        TestMethods(ScriptManager sm) {
            this.sm = sm;
        }
        public int add(int a, int b) {
            return a + b;
        }
        public String testReplace() {
            return sm.stringReplace("%target%");
        }
    }
}