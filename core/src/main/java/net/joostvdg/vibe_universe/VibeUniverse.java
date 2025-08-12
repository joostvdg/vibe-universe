package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

import java.awt.*;

public class VibeUniverse extends ApplicationAdapter {

    // ---------- App state (Splash → Sim) ----------
    private enum AppState { SPLASH, SIM }
    private AppState state = AppState.SPLASH;

    // ---------- Config ----------
    private static final float AU_TO_WORLD = 12f;                // 1 AU in world units
    private static final int ORBIT_SEGMENTS = 256;             // polyline detail
    private static final float MIN_TIME_SCALE = 0f;            // days / sec
    private static final float MAX_TIME_SCALE = 2000f;         // days / sec
    private static final float TIME_INC = 2f;                  // step when pressing [ or ]
    private static final float MOON_CLEARANCE_GAP = 0.40f;     // extra world units so orbit doesn't touch planet

    // ---------- Rendering ----------
    private PerspectiveCamera camera;
    private OrthographicCamera uiCam;
    private ModelBatch modelBatch;
    private Environment environment;
    private Model sunModel, planetModel, moonModel;
    private ModelInstance sunInstance;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch uiBatch;
    private BitmapFont font;
    private BitmapFont titleFont; // slightly larger for splash/title
    private GlyphLayout layout = new GlyphLayout();

    // ---------- Camera control (orbit/pan/zoom) ----------
    private final Vector3 camTarget = new Vector3(0, 0, 0);
    private float camDistance = 26f;
    private float camYawDeg = 0f;    // around Y
    private float camPitchDeg = 20f; // up/down
    private float zoomSpeed = 2f;
    private float orbitSpeed = 0.3f; // degrees per pixel drag
    private float panSpeed = 0.01f;  // world units per pixel
    // Default camera snapshot (filled in create())
    private float defaultCamDistance;
    private float defaultCamYawDeg;
    private float defaultCamPitchDeg;
    private final Vector3 defaultCamTarget = new Vector3();

    private final Vector2 lastMouse = new Vector2();
    private boolean rightDragging = false;
    private boolean middleDragging = false;

    // ---------- Simulation ----------
    private float simTimeDays = 0f;
    private float timeScaleDaysPerSec = 10f;                   // adjustable
    private boolean drawOrbits = true;
    private boolean paused = false;

    // ---------- Moons rendering mode ----------
    private enum MoonRenderMode { EXAGGERATED, REALISTIC, HIDDEN }
    private MoonRenderMode moonMode = MoonRenderMode.EXAGGERATED;

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

    // ---------- Splash UI ----------
    private Rectangle btnStatic  = new Rectangle();
    private Rectangle btnDynamic = new Rectangle();
    private boolean hoveredStatic  = false;
    private boolean hoveredDynamic = false;

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        shapeRenderer = new ShapeRenderer();
        uiBatch = new SpriteBatch();
        font = new BitmapFont();       // default
        titleFont = new BitmapFont();  // we’ll scale it up a bit
        titleFont.getData().setScale(1.4f);

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateCameraTransform();

        // Snapshot the initial camera pose for clean resets
        defaultCamDistance = camDistance;
        defaultCamYawDeg   = camYawDeg;
        defaultCamPitchDeg = camPitchDeg;
        defaultCamTarget.set(camTarget);

        uiCam = new OrthographicCamera();
        uiCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCam.update();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.7f, 1f));
        environment.add(new DirectionalLight().set(1f, 1f, 0.95f, -1f, -0.8f, -0.2f));

        ModelBuilder builder = new ModelBuilder();
        // Sun visual size
        sunModel = builder.createSphere(4f, 4f, 4f, 32, 32,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.85f, 0.4f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        sunInstance = new ModelInstance(sunModel);

        // Shared base models (we'll scale per-planet / per-moon)
        planetModel = builder.createSphere(1f, 1f, 1f, 24, 24,
                new Material(ColorAttribute.createDiffuse(new Color(0.7f, 0.8f, 1f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        moonModel = builder.createSphere(1f, 1f, 1f, 18, 18,
                new Material(ColorAttribute.createDiffuse(new Color(0.8f, 0.8f, 0.85f, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // --------- Planets (semi-major axis in AU, e, period days, color, radius_km) ---------
        addPlanet("Mercury", 1, 0.387098f, 0.2056f, 87.969f,  new Color(0.7f,0.7f,0.7f,1f), 2440);
        addPlanet("Venus",   2, 0.723332f, 0.0067f, 224.701f, new Color(1.0f,0.9f,0.7f,1f), 6052);
        addPlanet("Earth",   3, 1.000000f, 0.0167f, 365.256f, new Color(0.45f,0.7f,1f,1f),  6371);
        addPlanet("Mars",    4, 1.523679f, 0.0934f, 686.971f, new Color(1f,0.5f,0.35f,1f),  3390);
        addPlanet("Jupiter", 5, 5.2044f,   0.0489f, 4332.59f, new Color(0.9f,0.8f,0.6f,1f), 69911);
        addPlanet("Saturn",  6, 9.5826f,   0.0565f,10759.22f, new Color(0.95f,0.9f,0.75f,1f), 58232);
        addPlanet("Uranus",  7, 19.2184f,  0.046f, 30688.5f,  new Color(0.7f,0.9f,0.95f,1f), 25362);
        addPlanet("Neptune", 8, 30.1104f,  0.0097f, 60182f,   new Color(0.5f,0.7f,1f,1f),   24622);

        // Major moons (AU, e, period days) for a quick taste
        addMoon("Moon",     "Earth",   0.00257f,   0.0549f, 27.3217f, new Color(0.85f,0.85f,0.9f,1f),   1737);
        addMoon("Phobos",   "Mars",    0.0000627f, 0.0151f, 0.31891f, new Color(0.8f,0.7f,0.6f,1f),      11);
        addMoon("Deimos",   "Mars",    0.0001568f, 0.0005f, 1.263f,   new Color(0.8f,0.75f,0.6f,1f),      6);
        addMoon("Io",       "Jupiter", 0.002820f,  0.0041f, 1.769f,   new Color(0.95f,0.85f,0.5f,1f),  1821);
        addMoon("Europa",   "Jupiter", 0.004490f,  0.009f,  3.551f,   new Color(0.85f,0.9f,0.95f,1f),  1560);
        addMoon("Ganymede", "Jupiter", 0.007155f,  0.0013f, 7.155f,   new Color(0.8f,0.8f,0.8f,1f),    2634);
        addMoon("Callisto", "Jupiter", 0.012585f,  0.007f, 16.689f,   new Color(0.75f,0.75f,0.75f,1f), 2410);
        addMoon("Titan",    "Saturn",  0.008167f,  0.0288f,15.945f,   new Color(0.95f,0.8f,0.6f,1f),   2575);
        addMoon("Enceladus","Saturn",  0.001588f,  0.0047f, 1.370f,   new Color(0.9f,0.95f,1.0f,1f),    252);

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

        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean scrolled(float amountX, float amountY) {
                if (state != AppState.SIM) return false;
                camDistance += amountY * zoomSpeed;
                camDistance = MathUtils.clamp(camDistance, 6f, 400f);
                updateCameraTransform();
                return true;
            }
            @Override public boolean touchDown (int screenX, int screenY, int pointer, int button) {
                float y = uiCam.viewportHeight - screenY;

                if (state == AppState.SPLASH && button == Input.Buttons.LEFT) {
                    if (btnStatic.contains(screenX, y)) {
                        startSimulation();   // launch current sim
                        return true;
                    } else if (btnDynamic.contains(screenX, y)) {
                        // Placeholder: non-functional for now. Could flash or play a sound later.
                        return true;
                    }
                }

                lastMouse.set(screenX, screenY);
                if (button == Input.Buttons.RIGHT)  rightDragging  = true;
                if (button == Input.Buttons.MIDDLE) middleDragging = true;
                return true;
            }
            @Override public boolean mouseMoved (int screenX, int screenY) {
                if (state == AppState.SPLASH) {
                    float y = uiCam.viewportHeight - screenY;
                    hoveredStatic  = btnStatic.contains(screenX, y);
                    hoveredDynamic = btnDynamic.contains(screenX, y);
                }
                return false;
            }

            @Override public boolean touchDragged (int screenX, int screenY, int pointer) {
                if (state != AppState.SIM) return false;
                float dx = screenX - lastMouse.x;
                float dy = screenY - lastMouse.y;
                lastMouse.set(screenX, screenY);
                if (rightDragging) {
                    camYawDeg   -= dx * orbitSpeed;
                    camPitchDeg -= dy * orbitSpeed;
                    camPitchDeg = MathUtils.clamp(camPitchDeg, -85f, 85f);
                    updateCameraTransform();
                } else if (middleDragging) {
                    Vector3 right = new Vector3();
                    Vector3 up = new Vector3(0,1,0);
                    Vector3 forward = new Vector3();
                    getCameraBasis(right, up, forward);
                    camTarget.mulAdd(right, -dx * panSpeed * camDistance * 0.05f);
                    camTarget.mulAdd(up,    dy * panSpeed * camDistance * 0.05f);
                    updateCameraTransform();
                }
                return true;
            }
            @Override public boolean touchUp (int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.RIGHT) rightDragging = false;
                if (button == Input.Buttons.MIDDLE) middleDragging = false;
                return true;
            }
            @Override public boolean keyDown (int keycode) {
                if (state == AppState.SPLASH) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                        startSimulation(); // launches Static Sol Simplified
                        return true;
                    }
                    return false;
                }
                // SIM controls
                if (keycode == Input.Keys.M) {
                    switch (moonMode) {
                        case EXAGGERATED: moonMode = MoonRenderMode.REALISTIC; break;
                        case REALISTIC:   moonMode = MoonRenderMode.HIDDEN;    break;
                        case HIDDEN:      moonMode = MoonRenderMode.EXAGGERATED; break;
                    }
                    applyMoonMode();
                    return true;
                } else if (keycode == Input.Keys.R &&
                        (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))) {
                    resetCamera();
                    return true;
                }
                return false;
            }
        });

        // Apply initial moon mode
        applyMoonMode();
        updateMenuButtons();
    }

    private void startSimulation() {
        state = AppState.SIM;
        // optional: reset time so motion starts fresh
        simTimeDays = 0f;
        resetCamera(); // ensure consistent starting view
    }

    private void resetCamera() {
        camDistance = defaultCamDistance;
        camYawDeg   = defaultCamYawDeg;
        camPitchDeg = defaultCamPitchDeg;
        camTarget.set(defaultCamTarget);

        // clear any drag state so we don't "snap" on first move
        rightDragging = false;
        middleDragging = false;

        updateCameraTransform();
    }


    private void updateMenuButtons() {
        float w = uiCam.viewportWidth;
        float h = uiCam.viewportHeight;

        float btnW = 360f;
        float btnH = 56f;
        float spacing = 16f;

        float centerX = (w - btnW) / 2f;
        float baseY   = h * 0.35f;

        // First item (top)
        btnStatic.set(centerX, baseY + btnH + spacing, btnW, btnH);
        // Second item (bottom)
        btnDynamic.set(centerX, baseY, btnW, btnH);
    }


    private void applyMoonMode() {
        for (Moon m : moons) {
            if (moonMode == MoonRenderMode.HIDDEN) {
                m.visible = false;
            } else {
                m.visible = true;
                float scale = (moonMode == MoonRenderMode.EXAGGERATED) ? m.exaggeratedScale : m.realisticScale;
                m.setScaleAndRecompute(scale);
            }
        }
    }

    // Planet size mapping: cube-root scaling so sizes are readable
    private float planetVisualRadius(float radiusKm) {
        double earth = Math.cbrt(6371.0);
        double r = Math.cbrt(radiusKm);
        return (float)(1.2 * (r / earth)); // Earth ~1.2 units
    }
    private float moonVisualRadiusExaggerated(float radiusKm) {
        double moon = Math.cbrt(1737.0);
        double r = Math.cbrt(radiusKm);
        return (float)(0.8 * (r / moon)); // Bigger to be more visible
    }
    private float moonVisualRadiusRealistic(float radiusKm) {
        double moon = Math.cbrt(1737.0);
        double r = Math.cbrt(radiusKm);
        return (float)(0.35 * (r / moon)); // Smaller, closer to planet scale
    }

    private void addPlanet(String name, int index, float aAU, float e, float periodDays, Color color, int radiusKm) {
        float vis = planetVisualRadius(radiusKm);
        Body p = new Body(name, index, aAU, e, periodDays, color, planetModel, vis);
        planets.add(p);
    }

    private void addMoon(String name, String parentName, float aAU, float e, float periodDays, Color color, int radiusKm) {
        Body parent = null;
        for (Body p : planets) if (p.name.equals(parentName)) { parent = p; break; }
        if (parent == null) return;
        float visEx = moonVisualRadiusExaggerated(radiusKm);
        float visReal = moonVisualRadiusRealistic(radiusKm);
        Moon m = new Moon(name, parent, aAU, e, periodDays, color, moonModel, visEx, visReal);
        moons.add(m);
    }

    @Override
    public void render() {
        // Splash branch
        if (state == AppState.SPLASH) {
            renderSplash();
            return;
        }

        // Controls (SIM only)
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
        for (Moon m : moons) if (m.visible) m.updatePosition(simTimeDays);

        // 3D models
        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        for (Body p : planets) modelBatch.render(p.instance, environment);
        for (Moon m : moons) if (m.visible) modelBatch.render(m.instance, environment);
        modelBatch.end();

        // Orbit rings
        if (drawOrbits) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Gdx.gl.glLineWidth(1f);
            for (Body p : planets) p.drawOrbit(shapeRenderer);
            for (Moon m : moons) if (m.visible) m.drawOrbit(shapeRenderer);
            shapeRenderer.end();
        }

        // UI (orthographic, so it persists in fullscreen)
        uiCam.update();
        uiBatch.setProjectionMatrix(uiCam.combined);
        uiBatch.begin();
        float x = 12f, y = uiCam.viewportHeight - 12f;
        font.draw(uiBatch, "Vibe Universe — Data Feed", x, y);
        y -= 18f;
        for (PlanetInfo pi : feed) { font.draw(uiBatch, "#" + pi.index + "  " + pi.name + " — " + String.format("%.3f d", pi.periodDays), x, y); y -= 16f; }
        y -= 8f;
        font.draw(uiBatch, String.format("Sim time: %.1f days", simTimeDays), x, y); y -= 16f;
        font.draw(uiBatch, String.format("Time scale: %.1f days/sec  [%s]", timeScaleDaysPerSec, paused ? "PAUSED" : "RUNNING"), x, y); y -= 16f;
        font.draw(uiBatch, "Controls: Wheel=Zoom, RMB drag=Orbit, MMB drag=Pan, Space=Pause, [ / ]=Slower/Faster, O=Toggle Orbits, R=Reset Time, Shift+R=Reset Camera, M=Moons Mode", x, y);
        uiBatch.end();
    }

    private void renderSplash() {
        ScreenUtils.clear(0.03f, 0.03f, 0.06f, 1);

        // Simple starfield dots (kept lightweight)
        shapeRenderer.setProjectionMatrix(uiCam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Point);
        int stars = 300;
        for (int i = 0; i < stars; i++) {
            float sx = MathUtils.random(0f, uiCam.viewportWidth);
            float sy = MathUtils.random(0f, uiCam.viewportHeight);
            shapeRenderer.point(sx, sy, 0);
        }
        shapeRenderer.end();

        // Titles
        uiBatch.setProjectionMatrix(uiCam.combined);
        uiBatch.begin();

        String title = "Vibe Universe";
        layout.setText(titleFont, title);
        float tx = (uiCam.viewportWidth - layout.width) / 2f;
        float ty = uiCam.viewportHeight * 0.68f;
        titleFont.setColor(1f, 0.95f, 0.8f, 1f);
        titleFont.draw(uiBatch, layout, tx, ty);

        String subtitle = "A tiny solar-system sandbox in Java + libGDX";
        layout.setText(font, subtitle);
        float sx = (uiCam.viewportWidth - layout.width) / 2f;
        float sy = ty - 28f;
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(uiBatch, layout, sx, sy);

        uiBatch.end();

        // Draw buttons
        Color staticBase  = hoveredStatic  ? new Color(0.2f, 0.45f, 0.8f, 1f) : new Color(0.15f, 0.35f, 0.65f, 1f);
        Color dynamicBase = hoveredDynamic ? new Color(0.25f,0.25f,0.25f,1f)  : new Color(0.18f,0.18f,0.18f,1f);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setColor(new Color(staticBase.r, staticBase.g, staticBase.b, 0.88f));
        shapeRenderer.rect(btnStatic.x, btnStatic.y, btnStatic.width, btnStatic.height);

        shapeRenderer.setColor(new Color(dynamicBase.r, dynamicBase.g, dynamicBase.b, 0.88f));
        shapeRenderer.rect(btnDynamic.x, btnDynamic.y, btnDynamic.width, btnDynamic.height);
        shapeRenderer.end();

        // Button outlines
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.95f, 0.98f, 1f, 1f);
        shapeRenderer.rect(btnStatic.x, btnStatic.y, btnStatic.width, btnStatic.height);
        shapeRenderer.rect(btnDynamic.x, btnDynamic.y, btnDynamic.width, btnDynamic.height);
        shapeRenderer.end();

        // Button labels
        uiBatch.begin();
        font.setColor(1f, 1f, 1f, 1f);

        String lblStatic  = "Static Sol Simplified";
        layout.setText(font, lblStatic);
        float lxs = btnStatic.x + (btnStatic.width  - layout.width)/2f;
        float lys = btnStatic.y + (btnStatic.height + layout.height)/2f;
        font.draw(uiBatch, layout, lxs, lys);

        String lblDynamic = "Dynamic Sol Simplified (coming soon)";
        layout.setText(font, lblDynamic);
        float lxd = btnDynamic.x + (btnDynamic.width - layout.width)/2f;
        float lyd = btnDynamic.y + (btnDynamic.height + layout.height)/2f;
        font.draw(uiBatch, layout, lxd, lyd);

        // Hints
        String hint1 = "Press Enter / Space to start: Static Sol Simplified";
        layout.setText(font, hint1);
        font.setColor(0.8f, 0.85f, 0.95f, 1f);
        font.draw(uiBatch, layout, (uiCam.viewportWidth - layout.width)/2f, btnDynamic.y - 14f);

        uiBatch.end();
    }


    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();

        uiCam.setToOrtho(false, width, height);
        uiCam.update();

        updateMenuButtons();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        shapeRenderer.dispose();
        uiBatch.dispose();
        font.dispose();
        titleFont.dispose();
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

        final float aWorld;      // world units (base/original)
        final float bWorld;      // world units (base/original)

        final ModelInstance instance;
        final Array<Vector3> orbitPoints = new Array<>(ORBIT_SEGMENTS + 1);
        final Vector3 position = new Vector3();
        final float visualScale; // current visual radius (units)

        Body(String name, int index, float aAU, float e, float periodDays, Color color, Model sharedModel, float visualRadius) {
            this.name = name;
            this.index = index;
            this.aAU = aAU;
            this.e = e;
            this.periodDays = periodDays;
            this.color = new Color(color);

            this.aWorld = aAU * AU_TO_WORLD;
            this.bWorld = aWorld * (float)Math.sqrt(1.0 - (e * e));

            this.instance = new ModelInstance(sharedModel);
            this.instance.transform.setToScaling(visualRadius, visualRadius, visualRadius);
            this.visualScale = visualRadius;
            for (Material m : instance.materials) {
                m.set(ColorAttribute.createDiffuse(this.color));
            }
        }

        void initOrbit() {
            buildOrbitPolyline(aWorld, bWorld, e);
        }

        void buildOrbitPolyline(float a, float b, float ecc) {
            orbitPoints.clear();
            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t = (float)i / (float)ORBIT_SEGMENTS;
                float E = t * MathUtils.PI2;
                float x = a * (MathUtils.cos(E) - ecc);
                float z = b * MathUtils.sin(E);
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
            Vector3 scale = new Vector3();
            instance.transform.getScale(scale);
            instance.transform.setToScaling(scale);
            instance.transform.setTranslation(position);
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
        boolean visible = true;
        final float exaggeratedScale;
        final float realisticScale;

        // Effective (display) orbit parameters adjusted to clear parent
        private float aEff, bEff;

        Moon(String name, Body parent, float aAU, float e, float periodDays, Color color, Model sharedModel, float exaggeratedScale, float realisticScale) {
            super(name, 0, aAU, e, periodDays, color, sharedModel, exaggeratedScale);
            this.parent = parent;
            this.exaggeratedScale = exaggeratedScale;
            this.realisticScale = realisticScale;
            setScaleAndRecompute(exaggeratedScale);
        }

        void setScaleAndRecompute(float scale) {
            // Apply scaling while keeping translation
            Vector3 pos = new Vector3();
            instance.transform.getTranslation(pos);
            instance.transform.setToScaling(scale, scale, scale);
            instance.transform.setTranslation(pos);

            // Clearance: orbit stays outside planet+moon visual spheres + gap
            float clearance = parent.visualScale + scale + MOON_CLEARANCE_GAP; // world units
            float aBase = this.aWorld;
            float aNeeded = clearance / Math.max(0.0001f, (1f - this.e)); // ensure periapsis >= clearance
            this.aEff = Math.max(aBase, aNeeded);
            this.bEff = aEff * (float)Math.sqrt(1.0 - (this.e * this.e));

            // Rebuild orbit polyline around the parent
            buildOrbitPolyline(aEff, bEff, this.e);
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
            float x = aEff * (MathUtils.cos(E) - e);
            float z = bEff * MathUtils.sin(E);
            position.set(parent.position.x + x, 0f, parent.position.z + z);
            Vector3 scale = new Vector3();
            instance.transform.getScale(scale);
            instance.transform.setToScaling(scale);
            instance.transform.setTranslation(position);
        }
    }

    // ---------- Camera math helpers ----------
    private void updateCameraTransform() {
        float yawRad = camYawDeg * MathUtils.degreesToRadians;
        float pitchRad = camPitchDeg * MathUtils.degreesToRadians;
        float cx = camTarget.x + camDistance * MathUtils.cos(pitchRad) * MathUtils.cos(yawRad);
        float cz = camTarget.z + camDistance * MathUtils.cos(pitchRad) * MathUtils.sin(yawRad);
        float cy = camTarget.y + camDistance * MathUtils.sin(pitchRad);

        if (camera == null) {
            camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        camera.position.set(cx, cy, cz);
        camera.up.set(0, 1, 0);
        camera.lookAt(camTarget);
        camera.near = 0.1f;
        camera.far = 4000f;
        camera.update();
    }

    private void getCameraBasis(Vector3 outRight, Vector3 outUp, Vector3 outForward) {
        float yawRad = camYawDeg * MathUtils.degreesToRadians;
        float pitchRad = camPitchDeg * MathUtils.degreesToRadians;

        outForward.set(-MathUtils.cos(pitchRad) * MathUtils.cos(yawRad),
                -MathUtils.sin(pitchRad),
                -MathUtils.cos(pitchRad) * MathUtils.sin(yawRad)).nor();
        outRight.set(outForward.z, 0, -outForward.x).nor();
        outUp.set(outRight).crs(outForward).nor();
    }
}
