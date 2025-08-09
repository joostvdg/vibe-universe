package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes;
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
    private static final float AU_TO_WORLD = 8f;         // 1 AU == 8 world units (keeps Earth ~8 units away)
    private static final float TIME_SCALE_DAYS_PER_SEC = 10f; // sim time speed
    private static final int ORBIT_SEGMENTS = 256;       // polyline detail

    // ---------- Rendering ----------
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private Model sunModel, planetModel;
    private ModelInstance sunInstance;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch uiBatch;
    private BitmapFont font;

    // ---------- Simulation ----------
    private float simTimeDays = 0f;

    // Only render two planets for now (Earth + Mars)
    private final Array<Planet> planets = new Array<>();

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
        font = new BitmapFont(); // default font; we can swap to FreeType later

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 10f, 22f);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 400f;
        camera.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.7f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 0.95f, -1f, -0.8f, -0.2f));

        ModelBuilder builder = new ModelBuilder();
        sunModel = builder.createSphere(4f, 4f, 4f, 32, 32,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.85f, 0.4f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // Simple shared planet sphere (we’ll vary colors per instance)
        planetModel = builder.createSphere(1.2f, 1.2f, 1.2f, 24, 24,
                new Material(ColorAttribute.createDiffuse(new Color(0.7f, 0.8f, 1f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        sunInstance = new ModelInstance(sunModel);

        // --- Add Earth (#3) and Mars (#4) with real-ish orbital params ---
        // Params: name, index, semi-major axis (AU), eccentricity, orbital period (days), color
        planets.add(new Planet("Earth", 3, 1.000000f, 0.0167f, 365.256f, new Color(0.45f, 0.7f, 1f, 1f)));
        planets.add(new Planet("Mars",  4, 1.523679f, 0.0934f, 686.971f, new Color(1f, 0.5f, 0.35f, 1f)));

        // Precompute orbit polylines and create instances
        for (Planet p : planets) {
            p.initGraphics();
        }

        // Data feed (all 8 planets). Periods ~ sidereal days.
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

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        simTimeDays += TIME_SCALE_DAYS_PER_SEC * dt;

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Update planet transforms along their ellipses
        for (Planet p : planets) {
            p.updatePosition(simTimeDays);
        }

        // 3D models
        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        for (Planet p : planets) {
            modelBatch.render(p.instance, environment);
        }
        modelBatch.end();

        // Orbit polylines
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        // Thin lines; >1f often ignored by drivers, so keep 1f
        Gdx.gl.glLineWidth(1f);
        for (Planet p : planets) {
            p.drawOrbit(shapeRenderer);
        }
        shapeRenderer.end();

        // Data feed (UI)
        uiBatch.begin();
        float x = 12f, y = Gdx.graphics.getHeight() - 12f;
        font.draw(uiBatch, "Vibe Universe — Data Feed", x, y);
        y -= 18f;
        for (PlanetInfo pi : feed) {
            String line = "#" + pi.index + "  " + pi.name + " — " + String.format("%.3f d", pi.periodDays);
            font.draw(uiBatch, line, x, y);
            y -= 16f;
        }
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
    }

    // ------------------ Planet (elliptical orbit) ------------------

    private class Planet {
        final String name;
        final int index;
        final float aAU;         // semi-major axis (AU)
        final float e;           // eccentricity [0..1)
        final float periodDays;  // sidereal period (days)
        final Color color;

        final float aWorld;      // semi-major (world units)
        final float bWorld;      // semi-minor (world units)

        final ModelInstance instance;

        // Precomputed orbit polyline points (world space, coplanar with XY)
        final Array<Vector3> orbitPoints = new Array<>(ORBIT_SEGMENTS + 1);

        Planet(String name, int index, float aAU, float e, float periodDays, Color color) {
            this.name = name;
            this.index = index;
            this.aAU = aAU;
            this.e = e;
            this.periodDays = periodDays;
            this.color = new Color(color);

            this.aWorld = aAU * AU_TO_WORLD;
            this.bWorld = aWorld * (float)Math.sqrt(1.0 - (e * e));

            // Create a colored instance of the shared planet sphere
            this.instance = new ModelInstance(planetModel);
            // Tint via material color
            for (Material m : instance.materials) {
                m.set(ColorAttribute.createDiffuse(this.color));
            }
        }

        void initGraphics() {
            // Precompute ellipse polyline in orbital plane (XY), centered on Sun at origin.
            // Parametric via eccentric anomaly E ∈ [0, 2π]:
            // x = a (cosE - e), y = b sinE
            orbitPoints.clear();
            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t = (float)i / (float)ORBIT_SEGMENTS;
                float E = t * MathUtils.PI2;
                float x = aWorld * (MathUtils.cos(E) - e);
                float y = bWorld * MathUtils.sin(E);
                orbitPoints.add(new Vector3(x, 0f, y)); // use Z for the second axis so the camera looks down slightly
            }
        }

        void updatePosition(float simDays) {
            // Mean motion (rad/day)
            float n = MathUtils.PI2 / periodDays;
            // Mean anomaly (we set M0 = 0 at t=0 → periapsis)
            float M = n * simDays;

            // Normalize M to [-pi, pi] for better convergence
            M = (float)Math.atan2(Math.sin(M), Math.cos(M));

            // Solve Kepler's equation: M = E - e sin E for E
            float E = keplerSolve(M, e);

            // Position in orbital plane (XY) using eccentric anomaly
            float x = aWorld * (MathUtils.cos(E) - e);
            float y = bWorld * MathUtils.sin(E);

            // Place in XZ plane for nicer 3D view (y as Z)
            instance.transform.setToTranslation(x, 0f, y);
        }

        void drawOrbit(ShapeRenderer sr) {
            // Draw polyline as line segments
            for (int i = 0; i < orbitPoints.size - 1; i++) {
                Vector3 a = orbitPoints.get(i);
                Vector3 b = orbitPoints.get(i + 1);
                sr.line(a.x, a.y, a.z, b.x, b.y, b.z, color, color);
            }
        }

        // Newton-Raphson solver for E (eccentric anomaly)
        private float keplerSolve(float M, float e) {
            // Good starter (approx by M + e*sinM*(1+e*cosM))
            float E = M + e * MathUtils.sin(M) * (1f + e * MathUtils.cos(M));
            for (int i = 0; i < 8; i++) {
                float f = E - e * MathUtils.sin(E) - M;          // f(E)
                float fp = 1f - e * MathUtils.cos(E);            // f'(E)
                float d = f / fp;
                E -= d;
                if (Math.abs(d) < 1e-6f) break;
            }
            return E;
        }
    }
}
