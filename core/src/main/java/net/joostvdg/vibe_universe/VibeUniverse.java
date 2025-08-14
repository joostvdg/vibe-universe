package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

public class VibeUniverse extends ApplicationAdapter {

    // ---------- App state (Splash → Sim) ----------
    private enum AppState { SPLASH, SIM }
    private AppState state = AppState.SPLASH;

    // ---------- Config ----------
    private static final float AU_TO_WORLD = 12f;              // 1 AU in world units
    private static final int ORBIT_SEGMENTS = 256;             // polyline detail
    private static final float MIN_TIME_SCALE = 0f;            // days / sec
    private static final float MAX_TIME_SCALE = 2000f;         // days / sec
    private static final float TIME_INC = 2f;                  // step when pressing [ or ]
    private static final float MOON_CLEARANCE_GAP = 0.60f;     // world units margin

    // --- Orbit inclinations (deg, relative to ecliptic) ---
    private static final float INC_MERCURY = 7.0f;
    private static final float INC_VENUS   = 3.39f;
    private static final float INC_EARTH   = 0.0f;
    private static final float INC_MARS    = 1.85f;
    private static final float INC_JUPITER = 1.304f;
    private static final float INC_SATURN  = 2.485f;
    private static final float INC_URANUS  = 0.773f;
    private static final float INC_NEPTUNE = 1.769f;

    // --- Axial tilts (deg) for planet self-rotation orientation ---
    private static final float TILT_MERCURY = 0.03f;
    private static final float TILT_VENUS   = 177.4f; // retrograde-ish
    private static final float TILT_EARTH   = 23.44f;
    private static final float TILT_MARS    = 25.19f;
    private static final float TILT_JUPITER = 3.13f;
    private static final float TILT_SATURN  = 26.73f;
    private static final float TILT_URANUS  = 97.77f;
    private static final float TILT_NEPTUNE = 28.32f;

    // --- Rotation periods (days) ---
    private static final float ROT_MERCURY = 58.646f;
    private static final float ROT_VENUS   = -243.025f; // negative = retrograde spin look
    private static final float ROT_EARTH   = 0.997f;
    private static final float ROT_MARS    = 1.025957f;
    private static final float ROT_JUPITER = 0.41354f;   // 9.93h
    private static final float ROT_SATURN  = 0.4395f;    // 10.55h
    private static final float ROT_URANUS  = -0.718f;    // -17.2h retrograde
    private static final float ROT_NEPTUNE = 0.6713f;    // 16.11h

    // --- Compressed distance mode ---
    private static boolean compressDistances = false;  // toggle with 'C'
    private static float compressGamma = 0.6f;         // 1.0 = off; lower compresses more

    // ---------- Rendering ----------
    private PerspectiveCamera camera;
    private OrthographicCamera uiCam;
    private ModelBatch modelBatch;
    private Environment environment;
    private Model sunModel, planetModel, moonModel, saturnRingModel;
    private ModelInstance sunInstance, saturnRingInstance;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch uiBatch;
    private BitmapFont font;
    private BitmapFont titleFont;
    private GlyphLayout layout = new GlyphLayout();

    // ---------- Textures ----------
    private Texture txSun, txSunHalo;
    private Texture txMercury, txVenus, txEarth, txMars, txJupiter, txSaturn, txUranus, txNeptune, txMoonGeneric, txSaturnRings;

    // ---------- Lighting (new) ----------
    private PointLight sunPoint;
    private DirectionalLight fillWarm, fillCool;
    private boolean extendedLighting = true; // toggle 'L'

    // ---------- Camera control (orbit/pan/zoom) ----------
    private final Vector3 camTarget = new Vector3(0, 0, 0);
    private float camDistance = 26f;
    private float camYawDeg = 0f;    // around Y
    private float camPitchDeg = 20f; // up/down
    private float zoomSpeed = 2f;
    private float orbitSpeed = 0.3f; // degrees per pixel drag
    private float panSpeed = 0.01f;  // world units per pixel
    // Default camera snapshot
    private float defaultCamDistance, defaultCamYawDeg, defaultCamPitchDeg;
    private final Vector3 defaultCamTarget = new Vector3();
    private final Vector2 lastMouse = new Vector2();
    private boolean rightDragging = false;
    private boolean middleDragging = false;

    // ---------- Simulation ----------
    private float simTimeDays = 0f;
    private float timeScaleDaysPerSec = 10f; // adjustable
    private boolean drawOrbits = true;
    private boolean paused = false;

    // ---------- Axes (new) ----------
    private boolean showAxes = true; // toggle 'X'

    // ---------- Moons rendering mode ----------
    private enum MoonRenderMode { EXAGGERATED, REALISTIC, HIDDEN }
    private MoonRenderMode moonMode = MoonRenderMode.EXAGGERATED;

    private final Array<Body> planets = new Array<>();
    private final Array<Moon> moons = new Array<>();
    private Body saturnBody; // reference for rings

    // Data feed for planets
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
    private Rectangle btnExo     = new Rectangle();
    private boolean hoveredStatic  = false;
    private boolean hoveredDynamic = false;
    private boolean hoveredExo     = false;
    private Array<Vector2> starfield = new Array<>(300); // precomputed splash stars

    // ---------- Temps for perf ----------
    private final Vector3 tmpV3a = new Vector3();
    private final Vector3 tmpV3b = new Vector3();
    private final Quaternion tmpQ = new Quaternion();

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        shapeRenderer = new ShapeRenderer();
        uiBatch = new SpriteBatch();
        font = new BitmapFont();
        titleFont = new BitmapFont();
        titleFont.getData().setScale(1.4f);

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateCameraTransform();

        uiCam = new OrthographicCamera();
        uiCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiCam.update();

        // Environment base
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.16f, 0.16f, 0.20f, 1f));
        sunPoint = new PointLight().set(1.0f, 0.96f, 0.85f, 0f, 0f, 0f, 1400f);
        environment.add(sunPoint);

        // Fill lights (disabled/enabled via applyLightingProfile)
        fillWarm = new DirectionalLight().set(0.08f, 0.07f, 0.05f, -0.3f, -0.15f, -0.9f); // from "sunward" side
        fillCool = new DirectionalLight().set(0.06f, 0.07f, 0.10f,  0.2f,  0.4f,   0.9f); // cool space backlight

        // Models with UVs
        ModelBuilder builder = new ModelBuilder();
        int USAGE = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

        sunModel = builder.createSphere(4f, 4f, 4f, 32, 32, new Material(), USAGE);
        sunInstance = new ModelInstance(sunModel);

        planetModel = builder.createSphere(1f, 1f, 1f, 24, 24, new Material(), USAGE);
        moonModel   = builder.createSphere(1f, 1f, 1f, 18, 18, new Material(), USAGE);

        // Procedural textures
        txSun     = makeRadialSunTexture(512, 512, new Color(1f, 0.95f, 0.6f, 1f), new Color(1f, 0.6f, 0.2f, 1f));
        txSunHalo = makeSunHaloTexture(512, new Color(1.0f, 0.9f, 0.6f, 1f), 0.65f, 0.0f);

        txMercury = makeSpeckleTexture(512, 256, new Color(0.6f,0.6f,0.6f,1f), new Color(0.45f,0.45f,0.45f,1f), 0.002f);
        txVenus   = makeSpeckleTexture(512, 256, new Color(0.95f,0.85f,0.65f,1f), new Color(0.9f,0.8f,0.6f,1f), 0.001f);
        txEarth   = makeSpeckleTexture(512, 256, new Color(0.3f,0.55f,0.95f,1f), new Color(0.1f,0.4f,0.85f,1f), 0.0015f);
        txMars    = makeSpeckleTexture(512, 256, new Color(0.85f,0.45f,0.3f,1f), new Color(0.7f,0.35f,0.25f,1f), 0.002f);

        txJupiter = makeBandedTexture(512, 256, new Color[]{
                new Color(0.85f,0.78f,0.66f,1f), new Color(0.9f,0.82f,0.7f,1f),
                new Color(0.8f,0.7f,0.58f,1f),   new Color(0.9f,0.82f,0.7f,1f),
                new Color(0.78f,0.7f,0.58f,1f),  new Color(0.9f,0.82f,0.7f,1f)
        }, 0.08f);

        txSaturn  = makeBandedTexture(512, 256, new Color[]{
                new Color(0.95f,0.9f,0.75f,1f),  new Color(0.92f,0.86f,0.7f,1f),
                new Color(0.9f,0.84f,0.68f,1f),  new Color(0.92f,0.86f,0.7f,1f),
                new Color(0.88f,0.8f,0.64f,1f),  new Color(0.92f,0.86f,0.7f,1f)
        }, 0.06f);

        txUranus  = makeBandedTexture(512, 256, new Color[]{
                new Color(0.7f,0.9f,0.95f,1f),   new Color(0.65f,0.88f,0.95f,1f),
                new Color(0.72f,0.92f,0.96f,1f), new Color(0.65f,0.88f,0.95f,1f)
        }, 0.05f);

        txNeptune = makeBandedTexture(512, 256, new Color[]{
                new Color(0.35f,0.55f,1f,1f),    new Color(0.3f,0.5f,0.95f,1f),
                new Color(0.4f,0.6f,1f,1f),      new Color(0.3f,0.5f,0.95f,1f)
        }, 0.05f);

        txMoonGeneric = makeSpeckleTexture(512, 256, new Color(0.8f,0.8f,0.85f,1f), new Color(0.7f,0.7f,0.75f,1f), 0.0025f);

        // Apply textures + self-illumination to Sun
        for (Material m : sunInstance.materials) {
            m.set(TextureAttribute.createDiffuse(txSun));
            m.set(ColorAttribute.createDiffuse(Color.WHITE));
            m.set(ColorAttribute.createEmissive(1.0f, 0.92f, 0.55f, 1f));
        }

        // --------- Planets (semi-major axis in AU, e, period days, color, radius_km, inc, tilt, rot days) ---------
        addPlanet("Mercury", 1, 0.387098f, 0.2056f,  87.969f,  new Color(0.7f,0.7f,0.7f,1f), 2440, INC_MERCURY, TILT_MERCURY, ROT_MERCURY);
        addPlanet("Venus",   2, 0.723332f, 0.0067f, 224.701f, new Color(1.0f,0.9f,0.7f,1f), 6052, INC_VENUS,   TILT_VENUS,   ROT_VENUS);
        addPlanet("Earth",   3, 1.000000f, 0.0167f, 365.256f, new Color(0.45f,0.7f,1f,1f),  6371, INC_EARTH,   TILT_EARTH,   ROT_EARTH);
        addPlanet("Mars",    4, 1.523679f, 0.0934f, 686.971f, new Color(1f,0.5f,0.35f,1f),  3390, INC_MARS,    TILT_MARS,    ROT_MARS);
        addPlanet("Jupiter", 5, 5.2044f,   0.0489f, 4332.59f, new Color(0.9f,0.8f,0.6f,1f), 69911, INC_JUPITER, TILT_JUPITER, ROT_JUPITER);
        addPlanet("Saturn",  6, 9.5826f,   0.0565f,10759.22f, new Color(0.95f,0.9f,0.75f,1f), 58232, INC_SATURN,  TILT_SATURN,  ROT_SATURN);
        addPlanet("Uranus",  7, 19.2184f,  0.046f, 30688.5f,  new Color(0.7f,0.9f,0.95f,1f), 25362, INC_URANUS,  TILT_URANUS,  ROT_URANUS);
        addPlanet("Neptune", 8, 30.1104f,  0.0097f, 60182f,   new Color(0.5f,0.7f,1f,1f),   24622, INC_NEPTUNE, TILT_NEPTUNE, ROT_NEPTUNE);

        // Major moons (AU, e, period days) + inclinations (deg)
        addMoon("Moon",     "Earth",   0.00257f,   0.0549f, 27.3217f, new Color(0.85f,0.85f,0.9f,1f),   1737, 5.145f);
        addMoon("Phobos",   "Mars",    0.0000627f, 0.0151f, 0.31891f, new Color(0.8f,0.7f,0.6f,1f),      11,   1.08f);
        addMoon("Deimos",   "Mars",    0.0001568f, 0.0005f, 1.263f,   new Color(0.8f,0.75f,0.6f,1f),      6,   1.79f);
        addMoon("Io",       "Jupiter", 0.002820f,  0.0041f, 1.769f,   new Color(0.95f,0.85f,0.5f,1f),  1821, 0.05f);
        addMoon("Europa",   "Jupiter", 0.004490f,  0.009f,  3.551f,   new Color(0.85f,0.9f,0.95f,1f),  1560, 0.47f);
        addMoon("Ganymede", "Jupiter", 0.007155f,  0.0013f, 7.155f,   new Color(0.8f,0.8f,0.8f,1f),    2634, 0.20f);
        addMoon("Callisto", "Jupiter", 0.012585f,  0.007f, 16.689f,   new Color(0.75f,0.75f,0.75f,1f), 2410, 0.28f);
        addMoon("Titan",    "Saturn",  0.008167f,  0.0288f, 15.945f,  new Color(0.95f,0.8f,0.6f,1f),   2575, 0.35f);
        addMoon("Enceladus","Saturn",  0.001588f,  0.0047f, 1.370f,   new Color(0.9f,0.95f,1.0f,1f),    252, 0.01f);

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

        // Precompute orbit polylines
        for (Body p : planets) p.initOrbit();
        for (Moon m : moons)  m.initOrbit();

        // Find Saturn and build rings sized to inner moons
        for (Body p : planets) if (p.name.equals("Saturn")) { saturnBody = p; break; }
        rebuildSaturnRingsSizedToSystem();

        // Apply lighting profile according to toggle
        applyLightingProfile();

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        // Input
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
                        startSimulation();
                        return true;
                    } else if (btnDynamic.contains(screenX, y)) {
                        // Placeholder
                        return true;
                    } else if (btnExo.contains(screenX, y)) {
                        // Placeholder
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
                    hoveredExo     = btnExo.contains(screenX, y);
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
                if (button == Input.Buttons.RIGHT)  rightDragging  = false;
                if (button == Input.Buttons.MIDDLE) middleDragging = false;
                return true;
            }
            @Override public boolean keyDown (int keycode) {
                if (state == AppState.SPLASH) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                        startSimulation();
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
                } else if (keycode == Input.Keys.L) {
                    extendedLighting = !extendedLighting;
                    applyLightingProfile();
                    return true;
                } else if (keycode == Input.Keys.X) {
                    showAxes = !showAxes;
                    return true;
                } else if (keycode == Input.Keys.R &&
                        (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))) {
                    resetCamera();
                    return true;
                } else if (keycode == Input.Keys.SPACE) {
                    paused = !paused;
                    return true;
                } else if (keycode == Input.Keys.O) {
                    drawOrbits = !drawOrbits;
                    return true;
                } else if (keycode == Input.Keys.LEFT_BRACKET) {
                    timeScaleDaysPerSec = Math.max(MIN_TIME_SCALE, timeScaleDaysPerSec - TIME_INC);
                    return true;
                } else if (keycode == Input.Keys.RIGHT_BRACKET) {
                    timeScaleDaysPerSec = Math.min(MAX_TIME_SCALE, timeScaleDaysPerSec + TIME_INC);
                    return true;
                } else if (keycode == Input.Keys.C) {
                    compressDistances = !compressDistances;
                    for (Body p : planets) p.rebuildOrbitPolyline();
                    return true;
                } else if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_8) {
                    int idx = keycode - Input.Keys.NUM_0;
                    focusOnPlanetIndex(idx);
                    return true;
                }
                return false;
            }
        });

        // Apply initial moon mode
        applyMoonMode();
        updateMenuButtons();

        // Snapshot the initial camera pose for clean resets
        defaultCamDistance = camDistance;
        defaultCamYawDeg   = camYawDeg;
        defaultCamPitchDeg = camPitchDeg;
        defaultCamTarget.set(camTarget);

        // Precompute splash starfield (stable)
        for (int i = 0; i < 300; i++) {
            starfield.add(new Vector2(MathUtils.random(0f, uiCam.viewportWidth), MathUtils.random(0f, uiCam.viewportHeight)));
        }
    }

    private void startSimulation() {
        state = AppState.SIM;
        simTimeDays = 0f;
        resetCamera();
    }

    // Toggleable extended lighting without touching internal fields
    private void applyLightingProfile() {
        // Ambient tweak
        ColorAttribute amb = (ColorAttribute) environment.get(ColorAttribute.AmbientLight);
        if (amb != null) {
            if (extendedLighting) amb.color.set(0.22f, 0.22f, 0.26f, 1f);
            else                  amb.color.set(0.16f, 0.16f, 0.20f, 1f);
        }

        // Sun intensity (reset at origin)
        if (sunPoint != null) {
            float i = extendedLighting ? 2600f : 1400f;
            sunPoint.set(1.0f, 0.96f, 0.85f, 0f, 0f, 0f, i);
        }

        // Rebuild the directional fills using public methods
        environment.remove(fillWarm);
        environment.remove(fillCool);
        if (extendedLighting) {
            environment.add(fillWarm);
            environment.add(fillCool);
        }
    }


    private void updateMenuButtons() {
        float w = uiCam.viewportWidth;
        float h = uiCam.viewportHeight;

        float btnW = 360f;
        float btnH = 56f;
        float spacing = 16f;

        float centerX = (w - btnW) / 2f;
        float baseY   = h * 0.40f;

        // top → bottom
        btnStatic.set(centerX, baseY + (btnH + spacing) * 2f, btnW, btnH);
        btnDynamic.set(centerX, baseY + (btnH + spacing) * 1f, btnW, btnH);
        btnExo.set(centerX,     baseY,                         btnW, btnH);
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
        rebuildSaturnRingsSizedToSystem();
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

    private void addPlanet(String name, int index, float aAU, float e, float periodDays,
                           Color color, int radiusKm, float inclinationDeg, float spinTiltDeg, float rotationDays) {
        float vis = planetVisualRadius(radiusKm);
        Body p = new Body(name, index, aAU, e, periodDays, color, planetModel, vis, inclinationDeg, spinTiltDeg, rotationDays);
        applyPlanetTexture(p);
        planets.add(p);
    }

    private void addMoon(String name, String parentName,
                         float aAU, float e, float periodDays, Color color, int radiusKm, float inclinationDeg) {
        Body parent = null;
        for (Body p : planets) if (p.name.equals(parentName)) { parent = p; break; }
        if (parent == null) return;
        float visEx = moonVisualRadiusExaggerated(radiusKm);
        float visReal = moonVisualRadiusRealistic(radiusKm);
        Moon m = new Moon(name, parent, aAU, e, periodDays, color, moonModel, visEx, visReal, inclinationDeg);
        applyMoonTexture(m);
        moons.add(m);
    }

    @Override
    public void render() {
        // Splash branch
        if (state == AppState.SPLASH) {
            renderSplash();
            return;
        }

        float dt = Gdx.graphics.getDeltaTime();
        if (!paused) simTimeDays += timeScaleDaysPerSec * dt;

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Update positions + self-rotation
        for (Body p : planets) p.updatePosition(simTimeDays);
        for (Moon m : moons) if (m.visible) m.updatePosition(simTimeDays);

        // Update Saturn rings transform (tilt by axial tilt)
        if (saturnBody != null && saturnRingInstance != null) {
            saturnRingInstance.transform.idt();
            saturnRingInstance.transform.rotate(Vector3.X, saturnBody.spinTiltDeg);
            saturnRingInstance.transform.setTranslation(saturnBody.position);
        }

        // 3D draw
        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        for (Body p : planets) {
            if (p == saturnBody && saturnRingInstance != null) {
                modelBatch.render(saturnRingInstance, environment);
            }
            modelBatch.render(p.instance, environment);
        }
        for (Moon m : moons) if (m.visible) modelBatch.render(m.instance, environment);
        modelBatch.end();

        // Orbit rings
        if (drawOrbits) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (Body p : planets) p.drawOrbit(shapeRenderer);
            for (Moon m : moons) if (m.visible) m.drawOrbit(shapeRenderer);
            shapeRenderer.end();
        }

        // Axes (new)
        if (showAxes) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(0.95f, 0.95f, 1f, 1f);
            for (Body p : planets) {
                // axis in world space from planet's transform
//                tmpQ.idt();
//                p.instance.transform.getRotation(tmpQ);
//                Vector3 axis = new Vector3(0,1,0).rot(tmpQ).nor();
//                float len = Math.max(1.5f, p.visualScale * 2.2f);
//                Vector3 a = new Vector3(p.position).mulAdd(axis, -len);
//                Vector3 b = new Vector3(p.position).mulAdd(axis,  len);
//                shapeRenderer.line(a, b);
                // axis in world space from planet's transform
                p.instance.transform.getRotation(tmpQ); // get rotation as Quaternion
                Matrix4 rotM = new Matrix4().set(tmpQ); // convert to Matrix4

                // In older libGDX, Vector3.rot(Matrix4) is the supported overload:
                Vector3 axis = new Vector3(0, 1, 0).rot(rotM).nor();
                // If your Vector3 doesn't have rot(Matrix4), use this fallback instead:
                // Vector3 axis = new Vector3(0, 1, 0).mul(rotM).nor();

                float len = Math.max(1.5f, p.visualScale * 2.2f);
                Vector3 a = new Vector3(p.position).mulAdd(axis, -len);
                Vector3 b = new Vector3(p.position).mulAdd(axis,  len);
                shapeRenderer.line(a, b);

            }
            shapeRenderer.end();
        }

        // Sun glow billboard (screen-space additive)
        if (txSunHalo != null) {
            Vector3 sunScreen = tmpV3a.set(0f, 0f, 0f);
            camera.project(sunScreen);
            if (sunScreen.z >= 0f && sunScreen.z <= 1f) {
                float dist = camera.position.dst(0f, 0f, 0f);
                float sizePx = MathUtils.clamp(2400f / (dist + 0.001f), 80f, 600f);
                uiCam.update();
                uiBatch.setProjectionMatrix(uiCam.combined);
                uiBatch.begin();
                uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                uiBatch.draw(txSunHalo, sunScreen.x - sizePx * 0.5f, sunScreen.y - sizePx * 0.5f, sizePx, sizePx);
                uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                uiBatch.end();
            }
        }

        // UI (orthographic, so it persists in fullscreen)
        uiCam.update();
        uiBatch.setProjectionMatrix(uiCam.combined);
        uiBatch.begin();
        float x = 12f, y = uiCam.viewportHeight - 12f;
        font.draw(uiBatch, "Vibe Universe — Data Feed", x, y); y -= 18f;
        for (PlanetInfo pi : feed) { font.draw(uiBatch, "#" + pi.index + "  " + pi.name + " — " + String.format("%.3f d", pi.periodDays), x, y); y -= 16f; }
        y -= 6f;
        font.draw(uiBatch, String.format("Sim time: %.1f days | Time scale: %.1f d/s [%s] | FPS: %d | Compress: %s | Moons: %s | ExtLight: %s | Axes: %s",
                simTimeDays, timeScaleDaysPerSec, paused ? "PAUSED" : "RUN", Gdx.graphics.getFramesPerSecond(),
                compressDistances ? "ON" : "OFF", moonMode.name(), extendedLighting ? "ON" : "OFF", showAxes ? "ON" : "OFF"), x, y); y -= 18f;
        font.draw(uiBatch, "Keys: 1–8 Focus planet | C Distances | M Moons | O Orbits | X Axes | L Lighting | Space Pause | R Reset time | Shift+R Reset cam", x, y);
        uiBatch.end();

        // Optional: small labels near planets when close
        drawPlanetLabels();
    }

    private void renderSplash() {
        ScreenUtils.clear(0.03f, 0.03f, 0.06f, 1);

        // Simple starfield (precomputed points)
        shapeRenderer.setProjectionMatrix(uiCam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f,1f,1f,0.75f);
        for (Vector2 s : starfield) {
            shapeRenderer.circle(s.x, s.y, 1.2f);
        }
        shapeRenderer.end();

        // Titles
        uiBatch.setProjectionMatrix(uiCam.combined);
        uiBatch.begin();

        String title = "Vibe Universe";
        GlyphLayout layoutLocal = new GlyphLayout(titleFont, title);
        float tx = (uiCam.viewportWidth - layoutLocal.width) / 2f;
        float ty = uiCam.viewportHeight * 0.70f;
        titleFont.setColor(1f, 0.95f, 0.8f, 1f);
        titleFont.draw(uiBatch, layoutLocal, tx, ty);

        String subtitle = "Choose a mode";
        layoutLocal.setText(font, subtitle);
        float sx = (uiCam.viewportWidth - layoutLocal.width) / 2f;
        float sy = ty - 28f;
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(uiBatch, layoutLocal, sx, sy);
        uiBatch.end();

        // Buttons
        Color staticBase  = hoveredStatic  ? new Color(0.2f, 0.45f, 0.8f, 1f) : new Color(0.15f, 0.35f, 0.65f, 1f);
        Color dynamicBase = hoveredDynamic ? new Color(0.25f,0.25f,0.25f,1f)  : new Color(0.18f,0.18f,0.18f,1f);
        Color exoBase     = hoveredExo     ? new Color(0.28f,0.5f,0.28f,1f)   : new Color(0.2f,0.4f,0.2f,1f);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setColor(new Color(staticBase.r, staticBase.g, staticBase.b, 0.88f));
        shapeRenderer.rect(btnStatic.x, btnStatic.y, btnStatic.width, btnStatic.height);

        shapeRenderer.setColor(new Color(dynamicBase.r, dynamicBase.g, dynamicBase.b, 0.88f));
        shapeRenderer.rect(btnDynamic.x, btnDynamic.y, btnDynamic.width, btnDynamic.height);

        shapeRenderer.setColor(new Color(exoBase.r, exoBase.g, exoBase.b, 0.88f));
        shapeRenderer.rect(btnExo.x, btnExo.y, btnExo.width, btnExo.height);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.95f, 0.98f, 1f, 1f);
        shapeRenderer.rect(btnStatic.x, btnStatic.y, btnStatic.width, btnStatic.height);
        shapeRenderer.rect(btnDynamic.x, btnDynamic.y, btnDynamic.width, btnDynamic.height);
        shapeRenderer.rect(btnExo.x, btnExo.y, btnExo.width, btnExo.height);
        shapeRenderer.end();

        // Button labels + hints
        uiBatch.begin();
        font.setColor(1f, 1f, 1f, 1f);
        String lblStatic  = "Static Sol Simplified";
        GlyphLayout gl1 = new GlyphLayout(font, lblStatic);
        float lxs = btnStatic.x + (btnStatic.width  - gl1.width)/2f;
        float lys = btnStatic.y + (btnStatic.height + gl1.height)/2f;
        font.draw(uiBatch, gl1, lxs, lys);

        String lblDynamic = "Dynamic Sol Simplified (coming soon)";
        GlyphLayout gl2 = new GlyphLayout(font, lblDynamic);
        float lxd = btnDynamic.x + (btnDynamic.width - gl2.width)/2f;
        float lyd = btnDynamic.y + (btnDynamic.height + gl2.height)/2f;
        font.draw(uiBatch, gl2, lxd, lyd);

        String lblExo = "Exo Planets (coming soon)";
        GlyphLayout gl3 = new GlyphLayout(font, lblExo);
        float lxe = btnExo.x + (btnExo.width - gl3.width)/2f;
        float lye = btnExo.y + (btnExo.height + gl3.height)/2f;
        font.draw(uiBatch, gl3, lxe, lye);

        String hint1 = "Press Enter / Space to start: Static Sol Simplified";
        GlyphLayout gl4 = new GlyphLayout(font, hint1);
        font.setColor(0.8f, 0.85f, 0.95f, 1f);
        font.draw(uiBatch, gl4, (uiCam.viewportWidth - gl4.width)/2f, btnExo.y - 14f);
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
        if (saturnRingModel != null) saturnRingModel.dispose();

        if (txSun != null) txSun.dispose();
        if (txSunHalo != null) txSunHalo.dispose();
        if (txMercury != null) txMercury.dispose();
        if (txVenus != null) txVenus.dispose();
        if (txEarth != null) txEarth.dispose();
        if (txMars != null) txMars.dispose();
        if (txJupiter != null) txJupiter.dispose();
        if (txSaturn != null) txSaturn.dispose();
        if (txUranus != null) txUranus.dispose();
        if (txNeptune != null) txNeptune.dispose();
        if (txMoonGeneric != null) txMoonGeneric.dispose();
        if (txSaturnRings != null) txSaturnRings.dispose();
    }

    // ------------------ Orbital body ------------------

    private class Body {
        final String name;
        final int index;
        final float aAU;         // semi-major axis (AU)
        final float e;           // eccentricity [0..1)
        final float periodDays;  // sidereal period (days)
        final Color color;

        final ModelInstance instance;
        final Array<Vector3> orbitPoints = new Array<>(ORBIT_SEGMENTS + 1);
        final Vector3 position = new Vector3();
        final float visualScale; // radius in world units (render scale)

        final float inclinationDeg; // orbit tilt
        final float spinTiltDeg;    // axial tilt for self-rotation
        final float spinPeriodDays; // rotation period (days)

        Body(String name, int index, float aAU, float e, float periodDays, Color color,
             Model sharedModel, float visualRadius, float inclinationDeg, float spinTiltDeg, float spinPeriodDays) {
            this.name = name;
            this.index = index;
            this.aAU = aAU;
            this.e = e;
            this.periodDays = periodDays;
            this.color = new Color(color);
            this.visualScale = visualRadius;
            this.inclinationDeg = inclinationDeg;
            this.spinTiltDeg = spinTiltDeg;
            this.spinPeriodDays = spinPeriodDays;

            this.instance = new ModelInstance(sharedModel);
            this.instance.transform.setToScaling(visualRadius, visualRadius, visualRadius);

            for (Material m : instance.materials) {
                m.set(ColorAttribute.createDiffuse(Color.WHITE));
            }
        }

        void initOrbit() { rebuildOrbitPolyline(); }

        void rebuildOrbitPolyline() {
            float a = currentAWorld();
            float b = currentBWorld();
            orbitPoints.clear();
            float inc = inclinationDeg * MathUtils.degreesToRadians;
            float cosI = MathUtils.cos(inc);
            float sinI = MathUtils.sin(inc);

            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t = (float)i / (float)ORBIT_SEGMENTS;
                float E = t * MathUtils.PI2;
                float x = a * (MathUtils.cos(E) - e);
                float z0 = b * MathUtils.sin(E);
                float y = z0 * sinI;
                float z = z0 * cosI;
                orbitPoints.add(new Vector3(x, y, z));
            }
        }

        private float currentAWorld() {
            float aAUeff = compressDistances ? (float)Math.pow(aAU, compressGamma) : aAU;
            return aAUeff * AU_TO_WORLD;
        }
        private float currentBWorld() {
            float a = currentAWorld();
            return a * (float)Math.sqrt(1f - e*e);
        }

        void updatePosition(float simDays) {
            float n = MathUtils.PI2 / periodDays;
            float M = n * simDays;
            M = (float)Math.atan2(Math.sin(M), Math.cos(M));
            float E = keplerSolve(M, e);

            float a = currentAWorld();
            float b = currentBWorld();
            float x = a * (MathUtils.cos(E) - e);
            float z0 = b * MathUtils.sin(E);
            float inc = inclinationDeg * MathUtils.degreesToRadians;
            float y = z0 * MathUtils.sin(inc);
            float z = z0 * MathUtils.cos(inc);

            position.set(x, y, z);

            // Self-rotation around its own axis (tilted)
            float spinAngleDeg = 0f;
            if (spinPeriodDays != 0f) {
                spinAngleDeg = (simDays / spinPeriodDays) * 360f;
            }

            // Compose: scale -> tilt -> spin -> translate
            instance.transform.idt();
            instance.transform.setToScaling(visualScale, visualScale, visualScale);
            instance.transform.rotate(Vector3.X, spinTiltDeg);
            instance.transform.rotate(Vector3.Y, spinAngleDeg);
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

    private class Moon extends Body {
        final Body parent;
        boolean visible = true;
        final float exaggeratedScale;
        final float realisticScale;
        protected float aEff, bEff;

        Moon(String name, Body parent, float aAU, float e, float periodDays, Color color,
             Model sharedModel, float exaggeratedScale, float realisticScale, float inclinationDeg) {
            super(name, 0, aAU, e, periodDays, color, sharedModel, exaggeratedScale, inclinationDeg, 0f, 0f);
            this.parent = parent;
            this.exaggeratedScale = exaggeratedScale;
            this.realisticScale = realisticScale;
            setScaleAndRecompute(exaggeratedScale);
        }

        void initOrbit() { rebuildMoonOrbitPolyline(); }

        void setScaleAndRecompute(float scale) {
            Vector3 pos = new Vector3();
            instance.transform.getTranslation(pos);
            instance.transform.setToScaling(scale, scale, scale);
            instance.transform.setTranslation(pos);

            float clearance = parent.visualScale + scale + MOON_CLEARANCE_GAP; // world units
            float aBase = this.aAU * AU_TO_WORLD; // moons not distance-compressed
            float aNeeded = clearance / Math.max(0.0001f, (1f - this.e));
            this.aEff = Math.max(aBase, aNeeded);
            this.bEff = aEff * (float)Math.sqrt(1.0 - (this.e * this.e));

            rebuildMoonOrbitPolyline();
        }

        private void rebuildMoonOrbitPolyline() {
            orbitPoints.clear();
            float inc  = inclinationDeg * MathUtils.degreesToRadians;
            float cosI = MathUtils.cos(inc);
            float sinI = MathUtils.sin(inc);

            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t  = (float)i / (float)ORBIT_SEGMENTS;
                float E  = t * MathUtils.PI2;
                float x  = aEff * (MathUtils.cos(E) - e);
                float z0 = bEff * MathUtils.sin(E);
                float y = z0 * sinI;
                float z = z0 * cosI;
                orbitPoints.add(new Vector3(x, y, z));
            }
        }

        @Override
        void drawOrbit(ShapeRenderer sr) {
            for (int i = 0; i < orbitPoints.size - 1; i++) {
                Vector3 a = orbitPoints.get(i);
                Vector3 b = orbitPoints.get(i + 1);
                float ax = a.x + parent.position.x, ay = a.y + parent.position.y, az = a.z + parent.position.z;
                float bx = b.x + parent.position.x, by = b.y + parent.position.y, bz = b.z + parent.position.z;
                sr.line(ax, ay, az, bx, by, bz, color, color);
            }
        }

        @Override
        void updatePosition(float simDays) {
            float n = MathUtils.PI2 / periodDays;
            float M = n * simDays;
            M = (float)Math.atan2(Math.sin(M), Math.cos(M));
            float E = keplerSolve(M, e);

            float x  = aEff * (MathUtils.cos(E) - e);
            float z0 = bEff * MathUtils.sin(E);
            float inc = inclinationDeg * MathUtils.degreesToRadians;
            float y   = z0 * MathUtils.sin(inc);
            float z   = z0 * MathUtils.cos(inc);

            position.set(parent.position.x + x, parent.position.y + y, parent.position.z + z);

            Vector3 scale = new Vector3();
            instance.transform.getScale(scale);
            instance.transform.idt();
            instance.transform.setToScaling(scale);
            instance.transform.setTranslation(position);
        }

        float getPeriapsisWorld() { return aEff * (1f - e); }
    }

    // ---------- Camera & helpers ----------
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

    private void resetCamera() {
        camDistance = defaultCamDistance;
        camYawDeg   = defaultCamYawDeg;
        camPitchDeg = defaultCamPitchDeg;
        camTarget.set(defaultCamTarget);
        rightDragging = false;
        middleDragging = false;
        updateCameraTransform();
    }

    private void focusOnPlanetIndex(int idx1to8) {
        for (Body p : planets) {
            if (p.index == idx1to8) {
                camTarget.set(p.position);
                camDistance = Math.max(8f, p.visualScale * 6f);
                updateCameraTransform();
                break;
            }
        }
    }

    // ---------- Texture helpers ----------
    private Texture makeBandedTexture(int w, int h, Color[] bands, float jitter) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int nb = Math.max(1, bands.length);
        for (int y = 0; y < h; y++) {
            float t = (float)y / (float)(h - 1);
            float pos = t * nb;
            int i = Math.min(nb - 1, (int)(pos));
            float frac = pos - i;
            Color c0 = bands[i];
            Color c1 = bands[Math.min(nb - 1, i + 1)];
            float j = (MathUtils.random() - 0.5f) * jitter;
            float f = MathUtils.clamp(frac + j, 0f, 1f);
            float r = MathUtils.lerp(c0.r, c1.r, f);
            float g = MathUtils.lerp(c0.g, c1.g, f);
            float b = MathUtils.lerp(c0.b, c1.b, f);
            int rgba = Color.rgba8888(r, g, b, 1f);
            for (int x = 0; x < w; x++) pm.drawPixel(x, y, rgba);
        }
        Texture tx = new Texture(pm);
        tx.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return tx;
    }

    private Texture makeSpeckleTexture(int w, int h, Color base, Color speck, float density) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setColor(base);
        pm.fill();
        pm.setColor(speck);
        int count = (int)(w * h * density);
        for (int i = 0; i < count; i++) {
            int x = MathUtils.random(0, w - 1);
            int y = MathUtils.random(0, h - 1);
            pm.drawPixel(x, y);
        }
        Texture tx = new Texture(pm);
        tx.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return tx;
    }

    private Texture makeRadialSunTexture(int w, int h, Color inner, Color outer) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        float cx = w / 2f, cy = h / 2f, maxR = Math.max(w, h) / 2f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = (x - cx), dy = (y - cy);
                float r = (float)Math.sqrt(dx*dx + dy*dy) / maxR; // 0 → 1
                float t = MathUtils.clamp(r, 0f, 1f);
                float rcol = MathUtils.lerp(inner.r, outer.r, t);
                float gcol = MathUtils.lerp(inner.g, outer.g, t);
                float bcol = MathUtils.lerp(inner.b, outer.b, t);
                pm.drawPixel(x, y, Color.rgba8888(rcol, gcol, bcol, 1f));
            }
        }
        Texture tx = new Texture(pm);
        tx.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return tx;
    }

    private Texture makeSunHaloTexture(int size, Color color, float innerAlpha, float outerAlpha) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float cx = size * 0.5f, cy = size * 0.5f, maxR = size * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float r = (float)Math.sqrt(dx*dx + dy*dy) / maxR; // 0 at center → 1 at edge
                float t = MathUtils.clamp(r, 0f, 1f);
                float a = MathUtils.lerp(innerAlpha, outerAlpha, t * t * (3f - 2f * t));
                pm.drawPixel(x, y, Color.rgba8888(color.r, color.g, color.b, a));
            }
        }
        Texture tx = new Texture(pm);
        tx.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return tx;
    }

    private Texture makeSaturnRingTexture(int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            float u = x / (float)(w - 1);
            float r = MathUtils.lerp(0.88f, 0.96f, u);
            float g = MathUtils.lerp(0.82f, 0.92f, u);
            float b = MathUtils.lerp(0.70f, 0.86f, u);
            float alpha = smoothstep(0.05f, 0.95f, u);
            float bands = (MathUtils.sin(u * 70f) * 0.5f + 0.5f) * 0.12f
                    + (MathUtils.sin(u * 14f) * 0.5f + 0.5f) * 0.08f;
            r *= (1f - bands * 0.6f);
            g *= (1f - bands * 0.6f);
            b *= (1f - bands * 0.6f);
            int rgba = Color.rgba8888(r, g, b, alpha);
            for (int y = 0; y < h; y++) pm.drawPixel(x, y, rgba);
        }
        Texture tx = new Texture(pm);
        tx.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return tx;
    }

    private float smoothstep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void applyPlanetTexture(Body p) {
        Texture tx = null;
        switch (p.name) {
            case "Mercury": tx = txMercury; break;
            case "Venus":   tx = txVenus;   break;
            case "Earth":   tx = txEarth;   break;
            case "Mars":    tx = txMars;    break;
            case "Jupiter": tx = txJupiter; break;
            case "Saturn":  tx = txSaturn;  break;
            case "Uranus":  tx = txUranus;  break;
            case "Neptune": tx = txNeptune; break;
        }
        if (tx != null) {
            for (Material m : p.instance.materials) {
                m.set(TextureAttribute.createDiffuse(tx));
            }
        }
    }

    private void applyMoonTexture(Moon m) {
        if (txMoonGeneric != null) {
            for (Material mat : m.instance.materials) {
                mat.set(TextureAttribute.createDiffuse(txMoonGeneric));
            }
        }
    }

    // ---------- Rings ----------
    private Model createRingModel(float innerR, float outerR, int segments, Material material) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        long attrs = VertexAttributes.Usage.Position
                | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates;

        MeshPartBuilder mpb = mb.part("saturn_rings", GL20.GL_TRIANGLES, attrs, material);
        Vector3 n = new Vector3(0f, 1f, 0f);

        float dTheta = MathUtils.PI2 / segments;
        for (int i = 0; i < segments; i++) {
            float a0 = i * dTheta;
            float a1 = (i + 1) * dTheta;
            float c0 = MathUtils.cos(a0), s0 = MathUtils.sin(a0);
            float c1 = MathUtils.cos(a1), s1 = MathUtils.sin(a1);

            MeshPartBuilder.VertexInfo i0 = new MeshPartBuilder.VertexInfo()
                    .setPos(innerR * c0, 0f, innerR * s0).setNor(n).setUV(0f, (a0 / MathUtils.PI2));
            MeshPartBuilder.VertexInfo i1 = new MeshPartBuilder.VertexInfo()
                    .setPos(innerR * c1, 0f, innerR * s1).setNor(n).setUV(0f, (a1 / MathUtils.PI2));
            MeshPartBuilder.VertexInfo o0 = new MeshPartBuilder.VertexInfo()
                    .setPos(outerR * c0, 0f, outerR * s0).setNor(n).setUV(1f, (a0 / MathUtils.PI2));
            MeshPartBuilder.VertexInfo o1 = new MeshPartBuilder.VertexInfo()
                    .setPos(outerR * c1, 0f, outerR * s1).setNor(n).setUV(1f, (a1 / MathUtils.PI2));

            mpb.triangle(i0, i1, o1);
            mpb.triangle(i0, o1, o0);
        }
        return mb.end();
    }

    private void rebuildSaturnRingsSizedToSystem() {
        if (saturnBody == null) return;

        float inner = saturnBody.visualScale * 1.25f;
        float outer = saturnBody.visualScale * 2.0f;

        float minMoonPeri = Float.POSITIVE_INFINITY;
        for (Moon m : moons) {
            if (m.parent == saturnBody && m.visible) {
                minMoonPeri = Math.min(minMoonPeri, m.getPeriapsisWorld());
            }
        }
        if (minMoonPeri < Float.POSITIVE_INFINITY) {
            outer = Math.min(outer, minMoonPeri * 0.70f);
            inner = Math.min(inner, outer * 0.75f);
        }

        Material ringMat;
        if (saturnRingInstance == null) {
            if (txSaturnRings == null) txSaturnRings = makeSaturnRingTexture(1024, 64);
            ringMat = new Material(
                    new BlendingAttribute(true, 1f),
                    TextureAttribute.createDiffuse(txSaturnRings),
                    ColorAttribute.createDiffuse(1f,1f,1f,1f),
                    FloatAttribute.createShininess(8f)
            );
        } else {
            ringMat = saturnRingInstance.materials.first();
            if (saturnRingModel != null) saturnRingModel.dispose();
        }

        saturnRingModel = createRingModel(inner, outer, 128, ringMat);
        saturnRingInstance = new ModelInstance(saturnRingModel);
    }

    // ---------- Labels ----------
    private void drawPlanetLabels() {
        uiBatch.setProjectionMatrix(uiCam.combined);
        uiBatch.begin();
        font.setColor(0.9f, 0.95f, 1f, 0.9f);
        for (Body p : planets) {
            tmpV3b.set(p.position);
            camera.project(tmpV3b);
            if (tmpV3b.z >= 0f && tmpV3b.z <= 1f) {
                float dist = camera.position.dst(p.position);
                if (dist < 120f) {
                    String txt = p.name;
                    GlyphLayout gl = new GlyphLayout(font, txt);
                    if (txSunHalo != null) {
                        uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                        uiBatch.draw(txSunHalo, tmpV3b.x - gl.width*0.6f, tmpV3b.y - gl.height*0.9f, gl.width*1.2f, gl.height*1.8f);
                        uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    }
                    font.draw(uiBatch, gl, tmpV3b.x - gl.width/2f, tmpV3b.y + 12f);
                }
            }
        }
        uiBatch.end();
    }
}
