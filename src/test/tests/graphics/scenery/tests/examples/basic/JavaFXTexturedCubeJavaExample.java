package graphics.scenery.tests.examples.basic;

import cleargl.GLVector;
import com.sun.javafx.application.PlatformImpl;
import graphics.scenery.*;
import graphics.scenery.backends.Renderer;
import graphics.scenery.numerics.Random;
import graphics.scenery.utils.SceneryPanel;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class JavaFXTexturedCubeJavaExample {

    @Test
    public void testExample() throws Exception {
        JavaFXTexturedCubeApplication viewer = new JavaFXTexturedCubeApplication("scenery - JavaFXTexturedCubeExample",
            512, 512);
        viewer.main();
    }

    private class JavaFXTexturedCubeApplication extends SceneryBase {

        public JavaFXTexturedCubeApplication(String applicationName, int windowWidth, int windowHeight) {
            super(applicationName, windowWidth, windowHeight, false);
        }

        public void init() {

            CountDownLatch latch = new CountDownLatch(1);
            final SceneryPanel[] imagePanel = {null};

            PlatformImpl.startup(() -> {
            });

            Platform.runLater(() -> {

                Stage stage = new Stage();
                stage.setTitle(getApplicationName());

                StackPane stackPane = new StackPane();
                stackPane.setBackground(
                    new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));

                GridPane pane = new GridPane();
                Label label = new Label(getApplicationName());

                imagePanel[0] = new SceneryPanel(getWindowWidth(), getWindowHeight());

                GridPane.setHgrow(imagePanel[0], Priority.ALWAYS);
                GridPane.setVgrow(imagePanel[0], Priority.ALWAYS);

                GridPane.setFillHeight(imagePanel[0], true);
                GridPane.setFillWidth(imagePanel[0], true);

                GridPane.setHgrow(label, Priority.ALWAYS);
                GridPane.setHalignment(label, HPos.CENTER);
                GridPane.setValignment(label, VPos.BOTTOM);

                label.maxWidthProperty().bind(pane.widthProperty());

                pane.setStyle("-fx-background-color: rgb(20, 255, 20);" + "-fx-font-family: Consolas;"
                    + "-fx-font-weight: 400;" + "-fx-font-size: 1.2em;" + "-fx-text-fill: white;"
                    + "-fx-text-alignment: center;");

                label.setStyle("-fx-padding: 0.2em;" + "-fx-text-fill: black;");

                label.setTextAlignment(TextAlignment.CENTER);

                pane.add(imagePanel[0], 1, 1);
                pane.add(label, 1, 2);
                stackPane.getChildren().addAll(pane);

                Scene scene = new Scene(stackPane);
                stage.setScene(scene);
                stage.setOnCloseRequest(event -> {
                    getRenderer().setShouldClose(true);

                    Platform.runLater(Platform::exit);
                });
                stage.show();

                latch.countDown();
            });

            try {
                latch.await();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            setRenderer(
                Renderer.createRenderer(getHub(), getApplicationName(), getScene(), getWindowWidth(), getWindowHeight(), imagePanel[0]));
            getHub().add(SceneryElement.Renderer, getRenderer());

            Material boxmaterial = new Material();
            boxmaterial.setAmbient(new GLVector(1.0f, 0.0f, 0.0f));
            boxmaterial.setDiffuse(new GLVector(0.0f, 1.0f, 0.0f));
            boxmaterial.setSpecular(new GLVector(1.0f, 1.0f, 1.0f));

            boxmaterial.getTextures().put("diffuse",
                JavaFXTexturedCubeApplication.class.getResource("textures/helix.png").getFile());

            final Box box = new Box(new GLVector(1.0f, 1.0f, 1.0f), false);
            box.setName("le box du win");
            box.setMaterial(boxmaterial);
            getScene().addChild(box);

            PointLight[] lights = new PointLight[2];

            for (int i = 0; i < lights.length; i++) {
                lights[i] = new PointLight(5.0f);
                lights[i].setPosition(new GLVector(2.0f * i, 2.0f * i, 2.0f * i));
                lights[i].setEmissionColor(Random.randomVectorFromRange(3, 0.2f, 0.8f));
                lights[i].setIntensity(10.2f * (i + 1));
                getScene().addChild(lights[i]);
            }

            Camera cam = new DetachedHeadCamera();
            cam.setPosition(new GLVector(0.0f, 0.0f, 5.0f));
            cam.perspectiveCamera(50.0f, getWindowWidth(), getWindowHeight(), 0.1f, 1000.0f);
            cam.setActive(true);
            getScene().addChild(cam);

            new Thread(() -> {
                while (true) {
                    box.getRotation().rotateByAngleY(0.01f);
                    box.setNeedsUpdate(true);

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
}

