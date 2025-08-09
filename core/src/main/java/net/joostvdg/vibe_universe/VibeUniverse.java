
package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

public class VibeUniverse extends ApplicationAdapter {

    // ---------- Config ----------
    private static final float AU_TO_WORLD = 8f;               // 1 AU in world units
    private static final int ORBIT_SEGMENTS = 256;             // polyline detail
    private static final float MIN_TIME_SCALE = 0f;            // days / sec
    private static final float MAX_TIME_SCALE = 2000f;         // days / sec
    private static final float TIME_INC = 2f;                  // step when pressing [ or ]

    // ---------- Rendering ----------
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private Model sunModel, planetModel, moonModel;
    private ModelInstance sunInstance;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch uiBatch;
    private BitmapFont font;

    // ---------- Simulation ----------
    private float simTimeDays = 0f;
    private float timeScaleDaysPerSec = 10f;                   // adjustable
    private boolean drawOrbits = true;
    private boolean paused = false;

    private final Array<Body> planets = new Array<>();
    private final Array<Moon> moons = new Array<>();

    // Data feed for all 8 planets (name, # from Sun, period days)
    private static class PlanetInfo {
        final String name;
        final int index;
        final float periodDays;
        PlanetInfo(String name, int index, float periodDays) { this.name = name; this.index = index; this.periodDays = periodDays; }
    }
    private final Array<PlanetInfo> feed = new Array<>();

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        shapeRenderer = new ShapeRenderer();
        uiBatch = new SpriteBatch();
        font = new BitmapFont();

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 12f, 26f);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 800f;
        camera.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.7f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 0.95f, -1f, -0.8f, -0.2f));

        ModelBuilder builder = new ModelBuilder();
        sunModel = builder.createSphere(4f, 4f, 4f, 32, 32,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.85f, 0.4f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        planetModel = builder.createSphere(1.2f, 1.2f, 1.2f, 24, 24,
                new Material(ColorAttribute.createDiffuse(new Color(0.7f, 0.8f, 1f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        moonModel = builder.createSphere(0.5f, 0.5f, 0.5f, 18, 18,
                new Material(ColorAttribute.createDiffuse(new Color(0.8f, 0.8f, 0.85f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        sunInstance = new ModelInstance(sunModel);

        // --------- Planets (semi-major axis in AU, e, period days) ---------
        addPlanet("Mercury", 1, 0.387098f, 0.2056f, 87.969f, new Color(0.7f, 0.7f, 0.7f, 1f));
        addPlanet("Venus",   2, 0.723332f, 0.0067f, 224.701f, new Color(1.0f, 0.9f, 0.7f, 1f));
        addPlanet("Earth",   3, 1.000000f, 0.0167f, 365.256f, new Color(0.45f, 0.7f, 1f, 1f));
        addPlanet("Mars",    4, 1.523679f, 0.0934f, 686.971f, new Color(1f, 0.5f, 0.35f, 1f));
        addPlanet("Jupiter", 5, 5.2044f,   0.0489f, 4332.59f, new Color(0.9f, 0.8f, 0.6f, 1f));
        addPlanet("Saturn",  6, 9.5826f,   0.0565f, 10759.22f,new Color(0.95f, 0.9f, 0.75f, 1f));
        addPlanet("Uranus",  7, 19.2184f,  0.046f,  30688.5f, new Color(0.7f, 0.9f, 0.95f, 1f));
        addPlanet("Neptune", 8, 30.1104f,  0.0097f, 60182f,   new Color(0.5f, 0.7f, 1f, 1f));

        // Two moons (a couple): Earth's Moon and Mars' Phobos
        addMoon("Moon",   "Earth", 0.00257f, 0.0549f, 27.3217f, new Color(0.85f, 0.85f, 0.9f, 1f));
        addMoon("Phobos", "Mars",  0.0000627f, 0.0151f, 0.31891f, new Color(0.8f, 0.7f, 0.6f, 1f));

        // Data feed
        feed.addAll(
                new PlanetInfo("Mercury", 1, 87.969f),
                new PlanetInfo("Venus",   2, 224.701f),
                new PlanetInfo("Earth",   3, 365.256f),
                new PlanetInfo("Mars",    4, 686.971f),
                new PlanetInfo("Jupiter", 5, 4332.59f),
                new PlanetInfo("Saturn",  6, 10759.22f),
                new PlanetInfo("Uranus",  7, 30688.5f),
                new PlanetInfo("Neptune", 8, 60182f)
        );

        // Precompute orbits
        for (Body p : planets) p.initOrbit();
        for (Moon m : moons) m.initOrbit();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    }

    private void addPlanet(String name, int index, float aAU, float e, float periodDays, Color color) {
        Body p = new Body(name, index, aAU, e, periodDays, color, planetModel);
        planets.add(p);
    }

    private void addMoon(String name, String parentName, float aAU, float e, float periodDays, Color color) {
        Body parent = null;
        for (Body p : planets) if (p.name.equals(parentName)) { parent = p; break; }
        if (parent == null) return;
        Moon m = new Moon(name, parent, aAU, e, periodDays, color, moonModel);
        moons.add(m);
    }

    @Override
    public void render() {
        // Controls
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.O)) drawOrbits = !drawOrbits;
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) simTimeDays = 0f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
            timeScaleDaysPerSec = Math.max(MIN_TIME_SCALE, timeScaleDaysPerSec - TIME_INC);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
            timeScaleDaysPerSec = Math.min(MAX_TIME_SCALE, timeScaleDaysPerSec + TIME_INC);
        }

        float dt = Gdx.graphics.getDeltaTime();
        if (!paused) simTimeDays += timeScaleDaysPerSec * dt;

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Update positions
        for (Body p : planets) p.updatePosition(simTimeDays);
        for (Moon m : moons) m.updatePosition(simTimeDays);

        // 3D models
        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        for (Body p : planets) modelBatch.render(p.instance, environment);
        for (Moon m : moons) modelBatch.render(m.instance, environment);
        modelBatch.end();

        // Orbit rings
        if (drawOrbits) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Gdx.gl.glLineWidth(1f);
            for (Body p : planets) p.drawOrbit(shapeRenderer);
            for (Moon m : moons) m.drawOrbit(shapeRenderer);
            shapeRenderer.end();
        }

        // UI
        uiBatch.begin();
        float x = 12f, y = Gdx.graphics.getHeight() - 12f;
        font.draw(uiBatch, "Vibe Universe — Data Feed", x, y);
        y -= 18f;
        for (PlanetInfo pi : feed) { font.draw(uiBatch, "#" + pi.index + "  " + pi.name + " — " + String.format("%.3f d", pi.periodDays), x, y); y -= 16f; }

        y -= 8f;
        font.draw(uiBatch, String.format("Sim time: %.1f days", simTimeDays), x, y); y -= 16f;
        font.draw(uiBatch, String.format("Time scale: %.1f days/sec  [%s]", timeScaleDaysPerSec, paused ? "PAUSED" : "RUNNING"), x, y); y -= 16f;
        font.draw(uiBatch, "Controls: Space=Pause, [ / ]=Slower/Faster, O=Toggle Orbits, R=Reset Time", x, y);
        uiBatch.end();
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
        shapeRenderer.dispose();
        uiBatch.dispose();
        font.dispose();
        if (sunModel != null) sunModel.dispose();
        if (planetModel != null) planetModel.dispose();
        if (moonModel != null) moonModel.dispose();
    }

    // ------------------ Orbital body ------------------

    private static class Body {
        final String name;
        final int index;
        final float aAU;         // semi-major axis (AU)
        final float e;           // eccentricity [0..1)
        final float periodDays;  // sidereal period (days)
        final Color color;

        final float aWorld;      // world units
        final float bWorld;      // world units

        final ModelInstance instance;
        final Array<Vector3> orbitPoints = new Array<>(ORBIT_SEGMENTS + 1);
        final Vector3 position = new Vector3();

        Body(String name, int index, float aAU, float e, float periodDays, Color color, Model sharedModel) {
            this.name = name;
            this.index = index;
            this.aAU = aAU;
            this.e = e;
            this.periodDays = periodDays;
            this.color = new Color(color);

            this.aWorld = aAU * AU_TO_WORLD;
            this.bWorld = aWorld * (float)Math.sqrt(1.0 - (e * e));

            this.instance = new ModelInstance(sharedModel);
            for (Material m : instance.materials) {
                m.set(ColorAttribute.createDiffuse(this.color));
            }
        }

        void initOrbit() {
            orbitPoints.clear();
            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t = (float)i / (float)ORBIT_SEGMENTS;
                float E = t * MathUtils.PI2;
                float x = aWorld * (MathUtils.cos(E) - e);
                float z = bWorld * MathUtils.sin(E);
                orbitPoints.add(new Vector3(x, 0f, z));
            }
        }

        void updatePosition(float simDays) {
            float n = MathUtils.PI2 / periodDays;
            float M = n * simDays;
            M = (float)Math.atan2(Math.sin(M), Math.cos(M));
            float E = keplerSolve(M, e);
            float x = aWorld * (MathUtils.cos(E) - e);
            float z = bWorld * MathUtils.sin(E);
            position.set(x, 0f, z);
            instance.transform.setToTranslation(position);
        }

        void drawOrbit(ShapeRenderer sr) {
            for (int i = 0; i < orbitPoints.size - 1; i++) {
                Vector3 a = orbitPoints.get(i);
                Vector3 b = orbitPoints.get(i + 1);
                sr.line(a.x, a.y, a.z, b.x, b.y, b.z, color, color);
            }
        }

        protected float keplerSolve(float M, float e) {
            float E = M + e * MathUtils.sin(M) * (1f + e * MathUtils.cos(M));
            for (int i = 0; i < 8; i++) {
                float f = E - e * MathUtils.sin(E) - M;
                float fp = 1f - e * MathUtils.cos(E);
                float d = f / fp;
                E -= d;
                if (Math.abs(d) < 1e-6f) break;
            }
            return E;
        }
    }

    private static class Moon extends Body {
        final Body parent;

        Moon(String name, Body parent, float aAU, float e, float periodDays, Color color, Model sharedModel) {
            super(name, 0, aAU, e, periodDays, color, sharedModel);
            this.parent = parent;
        }

        @Override
        void drawOrbit(ShapeRenderer sr) {
            for (int i = 0; i < orbitPoints.size - 1; i++) {
                Vector3 a = orbitPoints.get(i);
                Vector3 b = orbitPoints.get(i + 1);
                float ax = a.x + parent.position.x, az = a.z + parent.position.z;
                float bx = b.x + parent.position.x, bz = b.z + parent.position.z;
                sr.line(ax, 0f, az, bx, 0f, bz, color, color);
            }
        }

        @Override
        void updatePosition(float simDays) {
            float n = MathUtils.PI2 / periodDays;
            float M = n * simDays;
            M = (float)Math.atan2(Math.sin(M), Math.cos(M));
            float E = keplerSolve(M, e);
            float x = aWorld * (MathUtils.cos(E) - e);
            float z = bWorld * MathUtils.sin(E);
            position.set(parent.position.x + x, 0f, parent.position.z + z);
            instance.transform.setToTranslation(position);
        }
    }
}
