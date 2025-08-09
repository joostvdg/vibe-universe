package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.Environment;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.graphics.VertexAttributes;


public class VibeUniverse extends ApplicationAdapter {
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private Model sunModel, planetModel;
    private ModelInstance sunInstance, planetInstance;
    private float angle;

    @Override
    public void create() {
        modelBatch = new ModelBatch();

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 8f, 18f);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 200f;
        camera.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.7f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 0.95f, -1f, -0.8f, -0.2f));

        ModelBuilder builder = new ModelBuilder();
        sunModel = builder.createSphere(4f, 4f, 4f, 32, 32,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.85f, 0.4f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        planetModel = builder.createSphere(1.2f, 1.2f, 1.2f, 24, 24,
                new Material(ColorAttribute.createDiffuse(new Color(0.4f, 0.6f, 1f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        sunInstance = new ModelInstance(sunModel);
        planetInstance = new ModelInstance(planetModel);
        planetInstance.transform.setToTranslation(8f, 0f, 0f);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        angle += dt * 30f * MathUtils.degreesToRadians; // 30 deg/sec

        float radius = 8f;
        float x = MathUtils.cos(angle) * radius;
        float z = MathUtils.sin(angle) * radius;
        planetInstance.transform.setToTranslation(x, 0f, z);

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        modelBatch.render(planetInstance, environment);
        modelBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        if (sunModel != null) sunModel.dispose();
        if (planetModel != null) planetModel.dispose();
    }
}
