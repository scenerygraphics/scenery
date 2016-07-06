package scenery.tests.examples;

import org.junit.Test;

/**
 * Created by kharrington on 7/6/16.
 */
public class JavaTexturedCubeExampleTest {
    @Test
    public void testExample() throws Exception {
        JavaTexturedCubeExample viewer = new JavaTexturedCubeExample( "scenery - TexturedCubeExample", 800, 600 );
        viewer.main();
    }
}
