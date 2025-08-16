package net.joostvdg.vibe_universe;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public class VibeUniverse extends ApplicationAdapter {

    // -------- States --------
    private enum AppState { SPLASH, EXO_MENU, SIM }
    private AppState state = AppState.SPLASH;

    private enum SpinMode { SMOOTH, REALISTIC }
    private SpinMode spinMode = SpinMode.SMOOTH;
    private static final float EARTH_DAY_DAYS = 0.99726968f; // sidereal-ish
    private static final float EARTH_SMOOTH_SECONDS = 20f;

// Per-body accumulated spin angle for SMOOTH mode


    // -------- Constants --------
    private static final float AU_TO_WORLD = 12f;
    private static final int ORBIT_SEGMENTS = 256;
    private static final float MOON_CLEARANCE_GAP = 0.60f;
    private static final float TIME_INC = 2f;

    // -------- Cameras/UI --------
    private PerspectiveCamera camera;
    private OrthographicCamera uiCam;
    private ModelBatch modelBatch;
    private Environment environment;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch uiBatch;
    private BitmapFont font, titleFont;
    private GlyphLayout layout = new GlyphLayout();

    // -------- Models/Textures --------
    private Model sunModel, planetModel, moonModel, saturnRingModel;
    private ModelInstance sunInstance, saturnRingInstance;
    private Texture txSun, txSunHalo, txSaturnRings;

    // -------- Lighting --------
    private PointLight sunPoint;

    // --- Lighting toggle ---
    private boolean extendedLighting = true;
    private DirectionalLight fillWarm = new DirectionalLight().set(0.18f, 0.14f, 0.12f,  -0.6f, -0.2f, -0.7f);
    private DirectionalLight fillCool = new DirectionalLight().set(0.14f, 0.16f, 0.20f,   0.4f, -0.3f,  0.6f);

    // --- Moon modes ---
    private enum MoonMode { EXAGGERATED, REALISTIC, HIDDEN }
    private MoonMode moonMode = MoonMode.EXAGGERATED;


    // -------- Camera control --------
    private final Vector3 camTarget = new Vector3();
    private float camDistance = 26f, camYawDeg = 0f, camPitchDeg = 20f;
    private float defaultCamDistance, defaultCamYawDeg, defaultCamPitchDeg;
    private final Vector3 defaultCamTarget = new Vector3();
    private final Vector2 lastMouse = new Vector2();
    private boolean rightDragging = false, middleDragging = false;

    // -------- Sim flags --------
    private float simTimeDays = 0f;
    private float timeScaleDaysPerSec = 10f;
    private boolean paused=false, drawOrbits=true, showAxes=true, compressDistances=false;
    private float compressGamma = 0.6f;

    // -------- Bodies --------
    private final Array<Body> planets = new Array<>();
    private final Array<Moon> moons = new Array<>();
    private Body saturnBody;

    // -------- Splash & Exo menu --------
    private Rectangle btnStatic=new Rectangle(), btnDynamic=new Rectangle(), btnExo=new Rectangle();
    private boolean hoveredStatic, hoveredDynamic, hoveredExo;
    private Array<Vector2> starfield = new Array<>(300);

    private Array<ExoEntry> exoEntries = new Array<>();
    private int exoIndex=0;
    private Rectangle btnPrev=new Rectangle(), btnNext=new Rectangle(), btnLoad=new Rectangle();
    private boolean hoveredPrev, hoveredNext, hoveredLoad;

    // -------- Data Feed --------
    private static class PlanetInfo { final String name; final int index; final float periodDays; PlanetInfo(String n,int i,float p){name=n;index=i;periodDays=p;} }
    private final Array<PlanetInfo> feed = new Array<>();

    // -------- Temps --------
    private final Vector3 tmpV3a=new Vector3(), tmpV3b=new Vector3();
    private final Quaternion tmpQ=new Quaternion();

    // ----- Model-wide scaling and realism toggles -----
    private float modelDistanceScale = 1.0f;     // multiplies AU_TO_WORLD for orbits
    private float planetSizeScale    = 1.0f;     // multiplies planet visual radii
    private float moonExagScale      = 1.0f;     // multiplies moon EXAGGERATED radii
    private float moonRealScale      = 1.0f;     // multiplies moon REALISTIC radii
    private boolean truePlanetProportionsDefault = false;
    private boolean trueMoonProportionsInRealisticDefault = false;

    // live toggles (can be changed with a key)
    private boolean truePlanetProportions = false;
    private boolean trueMoonProportionsInRealistic = false;


    // -------- Hardcoded SOL config --------
    private static final String SOL_MODEL_JSON = ""
            + "{"
            + "  \"name\":\"Static Sol Simplified\","
            + "  \"description\":\"Our current simplified Solar System with textures, tilts, and moons.\","
            + "  \"distanceScale\": 1.8,"
            + "  \"planetSizeScale\": 1.0,"
            + "  \"moonExaggeratedScale\": 1.0,"
            + "  \"moonRealisticScale\": 1.0,"
            + "  \"truePlanetProportions\": true,"
            + "  \"trueMoonProportionsInRealistic\": true,"
            + "  \"star\":{\"color\":[1.0,0.95,0.6],\"emissive\":[1.0,0.92,0.55],\"radiusVisual\":1.0},"
            + "  \"bodies\":["
            + "    {\"type\":\"planet\",\"name\":\"Mercury\",\"index\":1,\"aAU\":0.387098,\"e\":0.2056,\"periodDays\":87.969,\"radiusKm\":2440,\"inclinationDeg\":7.0,\"spinTiltDeg\":0.03,\"rotationDays\":58.646,\"texture\":{\"type\":\"speckle\",\"colorA\":[0.6,0.6,0.6],\"colorB\":[0.45,0.45,0.45],\"density\":0.002}},"
            + "    {\"type\":\"planet\",\"name\":\"Venus\",\"index\":2,\"aAU\":0.723332,\"e\":0.0067,\"periodDays\":224.701,\"radiusKm\":6052,\"inclinationDeg\":3.39,\"spinTiltDeg\":177.4,\"rotationDays\":-243.025,\"texture\":{\"type\":\"speckle\",\"colorA\":[0.95,0.85,0.65],\"colorB\":[0.9,0.8,0.6],\"density\":0.001}},"
            + "    {\"type\":\"planet\",\"name\":\"Earth\",\"index\":3,\"aAU\":1.0,\"e\":0.0167,\"periodDays\":365.256,\"radiusKm\":6371,\"inclinationDeg\":0.0,\"spinTiltDeg\":23.44,\"rotationDays\":0.997,\"texture\":{\"type\":\"speckle\",\"colorA\":[0.3,0.55,0.95],\"colorB\":[0.1,0.4,0.85],\"density\":0.0015},"
            + "      \"moons\":[{\"name\":\"Moon\",\"aAU\":0.00257,\"e\":0.0549,\"periodDays\":27.3217,\"radiusKm\":1737,\"inclinationDeg\":5.145}]"
            + "    },"
            + "    {\"type\":\"planet\",\"name\":\"Mars\",\"index\":4,\"aAU\":1.523679,\"e\":0.0934,\"periodDays\":686.971,\"radiusKm\":3390,\"inclinationDeg\":1.85,\"spinTiltDeg\":25.19,\"rotationDays\":1.025957,\"texture\":{\"type\":\"speckle\",\"colorA\":[0.85,0.45,0.3],\"colorB\":[0.7,0.35,0.25],\"density\":0.002},"
            + "      \"moons\":[{\"name\":\"Phobos\",\"aAU\":0.0000627,\"e\":0.0151,\"periodDays\":0.31891,\"radiusKm\":11,\"inclinationDeg\":1.08},"
            + "                {\"name\":\"Deimos\",\"aAU\":0.0001568,\"e\":0.0005,\"periodDays\":1.263,\"radiusKm\":6,\"inclinationDeg\":1.79}]"
            + "    },"
            + "    {\"type\":\"planet\",\"name\":\"Jupiter\",\"index\":5,\"aAU\":5.2044,\"e\":0.0489,\"periodDays\":4332.59,\"radiusKm\":69911,\"inclinationDeg\":1.304,\"spinTiltDeg\":3.13,\"rotationDays\":0.41354,\"texture\":{\"type\":\"banded\",\"bands\":[[0.85,0.78,0.66],[0.9,0.82,0.7],[0.8,0.7,0.58],[0.9,0.82,0.7],[0.78,0.7,0.58],[0.9,0.82,0.7]],\"jitter\":0.08},"
            + "      \"moons\":[{\"name\":\"Io\",\"aAU\":0.00282,\"e\":0.0041,\"periodDays\":1.769,\"radiusKm\":1821,\"inclinationDeg\":0.05},"
            + "                {\"name\":\"Europa\",\"aAU\":0.00449,\"e\":0.009,\"periodDays\":3.551,\"radiusKm\":1560,\"inclinationDeg\":0.47},"
            + "                {\"name\":\"Ganymede\",\"aAU\":0.007155,\"e\":0.0013,\"periodDays\":7.155,\"radiusKm\":2634,\"inclinationDeg\":0.2},"
            + "                {\"name\":\"Callisto\",\"aAU\":0.012585,\"e\":0.007,\"periodDays\":16.689,\"radiusKm\":2410,\"inclinationDeg\":0.28}]"
            + "    },"
            + "    {\"type\":\"planet\",\"name\":\"Saturn\",\"index\":6,\"aAU\":9.5826,\"e\":0.0565,\"periodDays\":10759.22,\"radiusKm\":58232,\"inclinationDeg\":2.485,\"spinTiltDeg\":26.73,\"rotationDays\":0.4395,\"texture\":{\"type\":\"banded\",\"bands\":[[0.95,0.9,0.75],[0.92,0.86,0.7],[0.9,0.84,0.68],[0.92,0.86,0.7],[0.88,0.8,0.64],[0.92,0.86,0.7]],\"jitter\":0.06},"
            + "      \"moons\":[{\"name\":\"Titan\",\"aAU\":0.008167,\"e\":0.0288,\"periodDays\":15.945,\"radiusKm\":2575,\"inclinationDeg\":0.35},"
            + "                {\"name\":\"Enceladus\",\"aAU\":0.001588,\"e\":0.0047,\"periodDays\":1.370,\"radiusKm\":252,\"inclinationDeg\":0.01}]"
            + "    },"
            + "    {\"type\":\"planet\",\"name\":\"Uranus\",\"index\":7,\"aAU\":19.2184,\"e\":0.046,\"periodDays\":30688.5,\"radiusKm\":25362,\"inclinationDeg\":0.773,\"spinTiltDeg\":97.77,\"rotationDays\":-0.718,\"texture\":{\"type\":\"banded\",\"bands\":[[0.7,0.9,0.95],[0.65,0.88,0.95],[0.72,0.92,0.96],[0.65,0.88,0.95]],\"jitter\":0.05}},"
            + "    {\"type\":\"planet\",\"name\":\"Neptune\",\"index\":8,\"aAU\":30.1104,\"e\":0.0097,\"periodDays\":60182,\"radiusKm\":24622,\"inclinationDeg\":1.769,\"spinTiltDeg\":28.32,\"rotationDays\":0.6713,\"texture\":{\"type\":\"banded\",\"bands\":[[0.35,0.55,1.0],[0.3,0.5,0.95],[0.4,0.6,1.0],[0.3,0.5,0.95]],\"jitter\":0.05}}"
            + "  ]"
            + "}";

    // -------- Inner classes --------
    private static class ExoEntry { String path,name,description; ExoEntry(String p,String n,String d){path=p;name=n;description=d;} }

    private class Body {
        final String name; final int index;
        protected final float aAU,e,periodDays;
        final Color color;
        final float visualScale;
        final float inclinationDeg, spinTiltDeg, spinPeriodDays;
        final ModelInstance instance;
        final Array<Vector3> orbitPoints = new Array<>(ORBIT_SEGMENTS + 1);
        final Vector3 position = new Vector3();
        float spinAngleDeg = 0f;     // used in SMOOTH mode

        Body(String name,int index,float aAU,float e,float periodDays,Color color,Model shared,float visualRadius,float inc,float tilt,float spinDays){
            this.name=name; this.index=index; this.aAU=aAU; this.e=e; this.periodDays=periodDays;
            this.color=new Color(color); this.visualScale=visualRadius; this.inclinationDeg=inc; this.spinTiltDeg=tilt; this.spinPeriodDays=spinDays;
            this.instance=new ModelInstance(shared);
            this.instance.transform.setToScaling(visualRadius,visualRadius,visualRadius);
            for (Material m:instance.materials) m.set(ColorAttribute.createDiffuse(Color.WHITE));
        }
        void initOrbit(){ rebuildOrbitPolyline(); }
        void rebuildOrbitPolyline(){
            float a=currentAWorld(), b=currentBWorld();
            orbitPoints.clear();
            float inc=inclinationDeg*MathUtils.degreesToRadians, cosI=MathUtils.cos(inc), sinI=MathUtils.sin(inc);
            for (int i=0;i<=ORBIT_SEGMENTS;i++){
                float t=i/(float)ORBIT_SEGMENTS, E=t*MathUtils.PI2;
                float x=a*(MathUtils.cos(E)-e), z0=b*MathUtils.sin(E);
                float y=z0*sinI, z=z0*cosI;
                orbitPoints.add(new Vector3(x,y,z));
            }
        }
        private float currentAWorld(){
            float aAUeff = compressDistances ? (float)Math.pow(aAU, compressGamma) : aAU;
            return aAUeff * AU_TO_WORLD * modelDistanceScale;
        }

        private float currentBWorld(){ float a=currentAWorld(); return a*(float)Math.sqrt(1f-e*e); }
        void updatePosition(float days){
            float n=MathUtils.PI2/periodDays; float M=n*days; M=(float)Math.atan2(Math.sin(M),Math.cos(M));
            float E=keplerSolve(M,e);
            float a=currentAWorld(), b=currentBWorld();
            float x=a*(MathUtils.cos(E)-e), z0=b*MathUtils.sin(E);
            float inc=inclinationDeg*MathUtils.degreesToRadians;
            float y=z0*MathUtils.sin(inc), z=z0*MathUtils.cos(inc);
            position.set(x,y,z);
//            float spin=(spinPeriodDays!=0f)? (days/spinPeriodDays)*360f:0f;
            // ----- Spin -----
            float spinDeg;
            if (spinPeriodDays != 0f) {
                if (spinMode == SpinMode.SMOOTH) {
                    // Earth: 360° per 20s → others proportional to their rotationDays
                    float secondsPerRotation = EARTH_SMOOTH_SECONDS * (spinPeriodDays / EARTH_DAY_DAYS);
                    float omegaDegPerSec = 360f / secondsPerRotation;
                    float dt = Gdx.graphics.getDeltaTime(); // real time
                    spinAngleDeg = (spinAngleDeg + omegaDegPerSec * dt) % 360f;
                    spinDeg = spinAngleDeg;
                } else {
                    // REALISTIC = tied to sim time (scales with timeScaleDaysPerSec)
                    spinDeg = (days / spinPeriodDays) * 360f;
                }
            } else {
                spinDeg = 0f;
            }

            instance.transform.idt();
            instance.transform.setToScaling(visualScale,visualScale,visualScale);
            instance.transform.rotate(Vector3.X, spinTiltDeg);
            instance.transform.rotate(Vector3.Y, spinDeg);
            instance.transform.setTranslation(position);
        }
        void drawOrbit(ShapeRenderer sr){
            for (int i=0;i<orbitPoints.size-1;i++){
                Vector3 a=orbitPoints.get(i), b=orbitPoints.get(i+1);
                sr.line(a.x,a.y,a.z, b.x,b.y,b.z, color,color);
            }
        }
        protected float keplerSolve(float M,float e){
            float E=M+e*MathUtils.sin(M)*(1f+e*MathUtils.cos(M));
            for (int i=0;i<8;i++){
                float f=E-e*MathUtils.sin(E)-M, fp=1f-e*MathUtils.cos(E);
                float d=f/fp; E-=d; if (Math.abs(d)<1e-6f) break;
            }
            return E;
        }
    }

    private class Moon extends Body {
        final Body parent; boolean visible = true;
        final float exaggeratedScale, realisticScale;

        // Effective ellipse for the moon (after size/clearance adjustments)
        float aEff, bEff;

        // Copy of eccentricity so we don't need to access a protected field from Body
        private final float ecc;

        Moon(String name, Body parent,
             float aAU, float e, float periodDays,
             Color color, Model shared,
             float exScale, float realScale, float inc) {
            // Body(String name,int index,float aAU,float e,float periodDays,Color color,Model shared,float visualRadius,float inc,float tilt,float spinDays){
            super(name, 0, aAU, e, periodDays, color, shared, exScale, inc, 0f, periodDays);
            this.parent = parent;
            this.exaggeratedScale = exScale;
            this.realisticScale   = realScale;
            this.ecc = e; // store locally
            setScaleAndRecompute(exScale);
        }

        @Override void initOrbit() { rebuildMoonOrbitPolyline(); }

        void setScaleAndRecompute(float scale) {
            // preserve position while resizing
            Vector3 pos = new Vector3();
            instance.transform.getTranslation(pos);
            instance.transform.setToScaling(scale, scale, scale);
            instance.transform.setTranslation(pos);

            // keep the orbit outside the planet's visual sphere (+ a small gap)
            float clearance = parent.visualScale + scale + MOON_CLEARANCE_GAP;

            // base semi-major axis from config (distance scale aware)
            float aBase = this.aAU * AU_TO_WORLD * modelDistanceScale;

            // minimum a needed so that periapsis = a(1 - e) stays outside clearance
            float denom = Math.max(0.0001f, (1f - ecc));
            float aNeeded = clearance / denom;

            this.aEff = Math.max(aBase, aNeeded);
            this.bEff = aEff * (float)Math.sqrt(Math.max(0f, 1f - ecc*ecc));

            rebuildMoonOrbitPolyline();
        }

        private void rebuildMoonOrbitPolyline() {
            orbitPoints.clear();
            float inc = inclinationDeg * MathUtils.degreesToRadians;
            float cosI = MathUtils.cos(inc), sinI = MathUtils.sin(inc);
            for (int i = 0; i <= ORBIT_SEGMENTS; i++) {
                float t = i / (float) ORBIT_SEGMENTS;
                float E = t * MathUtils.PI2;
                float x = aEff * (MathUtils.cos(E) - ecc);
                float z0 = bEff * MathUtils.sin(E);
                float y = z0 * sinI, z = z0 * cosI;
                orbitPoints.add(new Vector3(x, y, z));
            }
        }

        @Override
        void drawOrbit(ShapeRenderer sr) {
            if (parent == null) return;
            for (int i = 0; i < orbitPoints.size - 1; i++) {
                Vector3 a = orbitPoints.get(i);
                Vector3 b = orbitPoints.get(i + 1);
                float ax = a.x + parent.position.x;
                float ay = a.y + parent.position.y;
                float az = a.z + parent.position.z;
                float bx = b.x + parent.position.x;
                float by = b.y + parent.position.y;
                float bz = b.z + parent.position.z;
                sr.line(ax, ay, az, bx, by, bz, color, color);
            }
        }


        @Override void updatePosition(float days) {
            float n = MathUtils.PI2 / periodDays;
            float M = n * days;
            M = (float) Math.atan2(Math.sin(M), Math.cos(M));
            float E = keplerSolve(M, ecc);

            float x = aEff * (MathUtils.cos(E) - ecc);
            float z0 = bEff * MathUtils.sin(E);

            float inc = inclinationDeg * MathUtils.degreesToRadians;
            float y = z0 * MathUtils.sin(inc), z = z0 * MathUtils.cos(inc);

            position.set(parent.position.x + x, parent.position.y + y, parent.position.z + z);

            // ----- Spin (tidal lock by default) -----
            float spinDeg;
            if (spinPeriodDays != 0f) {
                if (spinMode == SpinMode.SMOOTH) {
                    float secondsPerRotation = EARTH_SMOOTH_SECONDS * (spinPeriodDays / EARTH_DAY_DAYS);
                    float omegaDegPerSec = 360f / secondsPerRotation;
                    float dt = Gdx.graphics.getDeltaTime();
                    spinAngleDeg = (spinAngleDeg + omegaDegPerSec * dt) % 360f;
                    spinDeg = spinAngleDeg;
                } else {
                    spinDeg = (days / spinPeriodDays) * 360f;
                }
            } else {
                spinDeg = 0f;
            }

            Vector3 s = new Vector3(); instance.transform.getScale(s);
            instance.transform.idt();
            instance.transform.setToScaling(s);
            instance.transform.rotate(Vector3.X, spinTiltDeg); // keep if you set any tilt for moons
            instance.transform.rotate(Vector3.Y, spinDeg);
            instance.transform.setTranslation(position);
        }

        float getPeriapsisWorld() { return aEff * (1f - ecc); }
    }


    @Override public void create(){
        modelBatch=new ModelBatch();
        shapeRenderer=new ShapeRenderer();
        uiBatch=new SpriteBatch();
        font=new BitmapFont(); titleFont=new BitmapFont(); titleFont.getData().setScale(1.4f);

        camera=new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateCamera();

        uiCam=new OrthographicCamera(); uiCam.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); uiCam.update();

        environment=new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.20f,0.20f,0.24f,1f));
        sunPoint=new PointLight().set(1.0f,0.96f,0.85f, 0f,0f,0f, 2000f); environment.add(sunPoint);

        applyLightingProfile();
        applyMoonMode();

        ModelBuilder mb=new ModelBuilder();
        int USAGE = VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.TextureCoordinates;
        sunModel = mb.createSphere(4f,4f,4f,32,32,new Material(),USAGE);
        planetModel = mb.createSphere(1f,1f,1f,24,24,new Material(),USAGE);
        moonModel   = mb.createSphere(1f,1f,1f,18,18,new Material(),USAGE);
        sunInstance = new ModelInstance(sunModel);

        txSun = makeRadialSunTexture(512,512,new Color(1f,0.95f,0.6f,1f), new Color(1f,0.6f,0.2f,1f));
        txSunHalo = makeSunHaloTexture(512, new Color(1.0f,0.9f,0.6f,1f), 0.65f, 0.0f);
        for (Material m: sunInstance.materials){
            m.set(TextureAttribute.createDiffuse(txSun));
            m.set(ColorAttribute.createDiffuse(Color.WHITE));
            m.set(ColorAttribute.createEmissive(1.0f,0.92f,0.55f,1f));
        }

        // Load SOL from embedded JSON
        loadModelFromJsonString(SOL_MODEL_JSON);
        rebuildSaturnRings();
        buildDataFeed();

        // Inputs
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean scrolled(float ax,float ay){
                if (state!=AppState.SIM) return false;
                camDistance += ay*2f; camDistance=MathUtils.clamp(camDistance,6f,400f); updateCamera(); return true;
            }
            @Override public boolean touchDown(int sx,int sy,int p,int button){
                float y=uiCam.viewportHeight - sy;
                if (state==AppState.SPLASH && button==Input.Buttons.LEFT){
                    if (btnStatic.contains(sx,y)){ startSim(); return true; }
                    if (btnDynamic.contains(sx,y)){ return true; }
                    if (btnExo.contains(sx,y)){ enterExoMenu(); return true; }
                } else if (state==AppState.EXO_MENU && button==Input.Buttons.LEFT){
                    if (btnPrev.contains(sx,y)){ if (exoEntries.size>0) exoIndex=(exoIndex-1+exoEntries.size)%exoEntries.size; return true; }
                    if (btnNext.contains(sx,y)){ if (exoEntries.size>0) exoIndex=(exoIndex+1)%exoEntries.size; return true; }
                    if (btnLoad.contains(sx,y)){ if (exoEntries.size>0) { loadExoAndStart(); } return true; }
                }
                lastMouse.set(sx,sy);
                if (button==Input.Buttons.RIGHT) rightDragging=true;
                if (button==Input.Buttons.MIDDLE) middleDragging=true;
                return true;
            }
            @Override public boolean mouseMoved(int sx,int sy){
                float y=uiCam.viewportHeight - sy;
                if (state==AppState.SPLASH){
                    hoveredStatic=btnStatic.contains(sx,y); hoveredDynamic=btnDynamic.contains(sx,y); hoveredExo=btnExo.contains(sx,y);
                } else if (state==AppState.EXO_MENU){
                    hoveredPrev=btnPrev.contains(sx,y); hoveredNext=btnNext.contains(sx,y); hoveredLoad=btnLoad.contains(sx,y);
                }
                return false;
            }
            @Override public boolean touchDragged(int sx,int sy,int p){
                if (state!=AppState.SIM) return false;
                float dx=sx-lastMouse.x, dy=sy-lastMouse.y; lastMouse.set(sx,sy);
                if (rightDragging){
                    camYawDeg -= dx*0.3f; camPitchDeg -= dy*0.3f; camPitchDeg=MathUtils.clamp(camPitchDeg,-85f,85f); updateCamera();
                } else if (middleDragging){
                    Vector3 right=new Vector3(), up=new Vector3(0,1,0), forward=new Vector3();
                    getCameraBasis(right,up,forward);
                    camTarget.mulAdd(right,-dx*0.01f*camDistance*0.05f);
                    camTarget.mulAdd(up,   dy*0.01f*camDistance*0.05f);
                    updateCamera();
                }
                return true;
            }
            @Override public boolean touchUp(int sx,int sy,int p,int button){
                if (button==Input.Buttons.RIGHT) rightDragging=false;
                if (button==Input.Buttons.MIDDLE) middleDragging=false;
                return true;
            }
            @Override public boolean keyDown(int key){
                if (state==AppState.SPLASH){
                    if (key==Input.Keys.ENTER || key==Input.Keys.SPACE){ startSim(); return true; }
                    if (key==Input.Keys.E){ enterExoMenu(); return true; }
                    return false;
                } else if (state==AppState.EXO_MENU){
                    if (key==Input.Keys.ESCAPE){ state=AppState.SPLASH; return true; }
                    if (key==Input.Keys.LEFT){ if (exoEntries.size>0) exoIndex=(exoIndex-1+exoEntries.size)%exoEntries.size; return true; }
                    if (key==Input.Keys.RIGHT){ if (exoEntries.size>0) exoIndex=(exoIndex+1)%exoEntries.size; return true; }
                    if (key==Input.Keys.ENTER){ if (exoEntries.size>0) { loadExoAndStart(); } return true; }
                    return false;
                }
                if (key==Input.Keys.L){
                    extendedLighting = !extendedLighting;
                    applyLightingProfile();
                    return true;
                }
                if (key==Input.Keys.M){
                    switch (moonMode){
                        case EXAGGERATED: moonMode = MoonMode.REALISTIC;  break;
                        case REALISTIC:   moonMode = MoonMode.HIDDEN;      break;
                        default:          moonMode = MoonMode.EXAGGERATED; break;
                    }
                    applyMoonMode();
                    return true;
                }
                if (key == Input.Keys.V) { // V = spin mode
                    spinMode = (spinMode == SpinMode.SMOOTH) ? SpinMode.REALISTIC : SpinMode.SMOOTH;
                    return true;
                }

                if (key == Input.Keys.T) {
                    truePlanetProportions = !truePlanetProportions;
                    // Rebuild all planet sizes
                    Array<Body> copyPlanets = new Array<>(planets);
                    for (Body p : copyPlanets) {
                        // re-evaluate size from stored source (radiusKm) → we’ll store it on Body to recompute
                        // If you don’t currently store radiusKm on Body, add a field and pass it in constructor.
                        // For minimal change, recompute scale by ratio against current scale:
                        // (If you already store radiusKm on Body, replace this block with a direct recompute)
                    }
                    // Reapply moon mode to re-size moons under the new rule
                    applyMoonMode();
                    // Rebuild orbits (clearance may change)
                    for (Body p : planets) p.rebuildOrbitPolyline();
                    rebuildSaturnRings();
                    return true;
                }


                if (key==Input.Keys.SPACE){ paused=!paused; return true; }
                if (key==Input.Keys.O){ drawOrbits=!drawOrbits; return true; }
                if (key==Input.Keys.X){ showAxes=!showAxes; return true; }
                if (key==Input.Keys.C){ compressDistances=!compressDistances; for (Body p:planets)p.rebuildOrbitPolyline(); return true; }
                if (key==Input.Keys.LEFT_BRACKET){ timeScaleDaysPerSec=Math.max(0f, timeScaleDaysPerSec-TIME_INC); return true; }
                if (key==Input.Keys.RIGHT_BRACKET){ timeScaleDaysPerSec=Math.min(2000f, timeScaleDaysPerSec+TIME_INC); return true; }
                if (key==Input.Keys.R && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)||Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))){ resetCamera(); return true; }
                if (key>=Input.Keys.NUM_1 && key<=Input.Keys.NUM_8){ int idx=key-Input.Keys.NUM_0; focusOnPlanet(idx); return true; }
                return false;
            }
        });

        // UI elements
        updateSplashButtons();
        updateExoButtons();
        for (int i=0;i<300;i++) starfield.add(new Vector2(MathUtils.random(0f, uiCam.viewportWidth), MathUtils.random(0f, uiCam.viewportHeight)));

        // camera snapshot
        defaultCamDistance=camDistance; defaultCamYawDeg=camYawDeg; defaultCamPitchDeg=camPitchDeg; defaultCamTarget.set(camTarget);

        // scan exo
        refreshExoEntries();
    }

    private void startSim(){ state=AppState.SIM; simTimeDays=0f; resetCamera(); }
    private void enterExoMenu(){ state=AppState.EXO_MENU; }

    private void refreshExoEntries(){
        exoEntries.clear();
        FileHandle base = Gdx.files.internal("models/exo");
        if (base.exists()){
            for (FileHandle f : base.list("json")){
                try{
                    JsonValue r = new JsonReader().parse(f);
                    exoEntries.add(new ExoEntry(f.path(), r.getString("name","Unknown"), r.getString("description","")));
                }catch(Exception ignore){}
            }
        }
        exoIndex=0;
    }

    private void loadExoAndStart(){
        ExoEntry e = exoEntries.get(exoIndex);
        loadModelFromFile(e.path);
        buildDataFeed();
        rebuildSaturnRings();
        startSim();
    }

    private void buildDataFeed(){
        feed.clear();
        for (Body p:planets) if (p.index>0) feed.add(new PlanetInfo(p.name,p.index,p.periodDays));
    }

    @Override public void render(){
        if (state==AppState.SPLASH){ renderSplash(); return; }
        if (state==AppState.EXO_MENU){ renderExoMenu(); return; }

        float dt=Gdx.graphics.getDeltaTime();
        if (!paused) simTimeDays += timeScaleDaysPerSec * dt;

        ScreenUtils.clear(0.05f,0.05f,0.08f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        for (Body p:planets) p.updatePosition(simTimeDays);
        for (Moon m:moons) if (m.visible) m.updatePosition(simTimeDays);

        if (saturnBody!=null && saturnRingInstance!=null){
            saturnRingInstance.transform.idt();
            saturnRingInstance.transform.rotate(Vector3.X, saturnBody.spinTiltDeg);
            saturnRingInstance.transform.setTranslation(saturnBody.position);
        }

        modelBatch.begin(camera);
        modelBatch.render(sunInstance, environment);
        for (Body p:planets){
            if (p==saturnBody && saturnRingInstance!=null) modelBatch.render(saturnRingInstance, environment);
            modelBatch.render(p.instance, environment);
        }
        for (Moon m:moons) if (m.visible) modelBatch.render(m.instance, environment);
        modelBatch.end();

        if (drawOrbits){
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (Body p:planets) p.drawOrbit(shapeRenderer);
            for (Moon m:moons) if (m.visible) m.drawOrbit(shapeRenderer);
            shapeRenderer.end();
        }

        if (showAxes){
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(0.95f,0.95f,1f,1f);
            for (Body p:planets){
                p.instance.transform.getRotation(tmpQ);
                Matrix4 rotM=new Matrix4().set(tmpQ);
                Vector3 axis = new Vector3(0,1,0).mul(rotM).nor();
                float len=Math.max(1.5f, p.visualScale*2.2f);
                Vector3 a=new Vector3(p.position).mulAdd(axis,-len);
                Vector3 b=new Vector3(p.position).mulAdd(axis, len);
                shapeRenderer.line(a,b);
            }
            shapeRenderer.end();
        }

        if (txSunHalo!=null){
            Vector3 ss=tmpV3a.set(0,0,0); camera.project(ss);
            if (ss.z>=0f && ss.z<=1f){
                float dist=camera.position.dst(0,0,0);
                float sizePx=MathUtils.clamp(2400f/(dist+0.001f),80f,600f);
                uiCam.update(); uiBatch.setProjectionMatrix(uiCam.combined);
                uiBatch.begin(); uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                uiBatch.draw(txSunHalo, ss.x-sizePx*0.5f, ss.y-sizePx*0.5f, sizePx,sizePx);
                uiBatch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                uiBatch.end();
            }
        }

        // HUD
        uiCam.update(); uiBatch.setProjectionMatrix(uiCam.combined); uiBatch.begin();
        float x=12f, y=uiCam.viewportHeight-12f;
        font.draw(uiBatch,"Vibe Universe — Data Feed",x,y); y-=18f;
        for (PlanetInfo pi:feed){ font.draw(uiBatch, "#"+pi.index+"  "+pi.name+" — "+String.format("%.3f d", pi.periodDays), x,y); y-=16f; }
        y-=6f;
        font.draw(uiBatch, String.format("Sim time: %.1f d | Scale: %.1f d/s [%s] | FPS: %d | Compress: %s | Axes: %s",
                simTimeDays, timeScaleDaysPerSec, paused? "PAUSED":"RUN", Gdx.graphics.getFramesPerSecond(),
                compressDistances? "ON":"OFF", showAxes? "ON":"OFF"), x,y);

        y -= 20f;
        font.draw(uiBatch, "Controls:  Right-drag=orbit  Middle-drag=pan  Wheel=zoom  Shift+R=reset  1-8=focus", x, y); y -= 16f;
        font.draw(uiBatch, "Toggle:    Space=pause  O=orbits  X=axes  C=distance-compress  L=extended-light  M=moon-mode, V=spin mode (Smooth/Realistic)\n", x, y); y -= 16f;
        font.draw(uiBatch, "Moon mode: EXAGGERATED → REALISTIC → HIDDEN", x, y);

        uiBatch.end();
    }

    private void renderSplash(){
        ScreenUtils.clear(0.03f,0.03f,0.06f,1);
        shapeRenderer.setProjectionMatrix(uiCam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f,1f,1f,0.75f);
        for (Vector2 s:starfield) shapeRenderer.circle(s.x,s.y,1.2f);
        shapeRenderer.end();

        uiBatch.setProjectionMatrix(uiCam.combined); uiBatch.begin();
        String title="Vibe Universe";
        GlyphLayout tl=new GlyphLayout(titleFont,title);
        float tx=(uiCam.viewportWidth-tl.width)/2f, ty=uiCam.viewportHeight*0.70f;
        titleFont.setColor(1f,0.95f,0.8f,1f); titleFont.draw(uiBatch, tl, tx, ty);
        String subtitle="Choose a mode"; GlyphLayout sub=new GlyphLayout(font,subtitle);
        font.setColor(0.85f,0.88f,1f,1f); font.draw(uiBatch, sub, (uiCam.viewportWidth-sub.width)/2f, ty-28f);
        uiBatch.end();

        Color staticBase= hoveredStatic? new Color(0.2f,0.45f,0.8f,1f) : new Color(0.15f,0.35f,0.65f,1f);
        Color dynamicBase= hoveredDynamic? new Color(0.25f,0.25f,0.25f,1f) : new Color(0.18f,0.18f,0.18f,1f);
        Color exoBase= hoveredExo? new Color(0.28f,0.5f,0.28f,1f) : new Color(0.2f,0.4f,0.2f,1f);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setColor(new Color(staticBase.r,staticBase.g,staticBase.b,0.88f));
        shapeRenderer.rect(btnStatic.x,btnStatic.y,btnStatic.width,btnStatic.height);
        shapeRenderer.setColor(new Color(dynamicBase.r,dynamicBase.g,dynamicBase.b,0.88f));
        shapeRenderer.rect(btnDynamic.x,btnDynamic.y,btnDynamic.width,btnDynamic.height);
        shapeRenderer.setColor(new Color(exoBase.r,exoBase.g,exoBase.b,0.88f));
        shapeRenderer.rect(btnExo.x,btnExo.y,btnExo.width,btnExo.height);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.95f,0.98f,1f,1f);
        shapeRenderer.rect(btnStatic.x,btnStatic.y,btnStatic.width,btnStatic.height);
        shapeRenderer.rect(btnDynamic.x,btnDynamic.y,btnDynamic.width,btnDynamic.height);
        shapeRenderer.rect(btnExo.x,btnExo.y,btnExo.width,btnExo.height);
        shapeRenderer.end();

        uiBatch.begin(); font.setColor(1f,1f,1f,1f);
        String lblStatic="Static Sol Simplified"; GlyphLayout gl1=new GlyphLayout(font,lblStatic);
        font.draw(uiBatch, gl1, btnStatic.x+(btnStatic.width-gl1.width)/2f, btnStatic.y+(btnStatic.height+gl1.height)/2f);
        String lblDynamic="Dynamic Sol Simplified (coming soon)"; GlyphLayout gl2=new GlyphLayout(font,lblDynamic);
        font.draw(uiBatch, gl2, btnDynamic.x+(btnDynamic.width-gl2.width)/2f, btnDynamic.y+(btnDynamic.height+gl2.height)/2f);
        String lblExo="Exo Planets"; GlyphLayout gl3=new GlyphLayout(font,lblExo);
        font.draw(uiBatch, gl3, btnExo.x+(btnExo.width-gl3.width)/2f, btnExo.y+(btnExo.height+gl3.height)/2f);
        String hint="Press E for Exo Planets"; GlyphLayout gl4=new GlyphLayout(font,hint);
        font.setColor(0.8f,0.85f,0.95f,1f);
        font.draw(uiBatch, gl4, (uiCam.viewportWidth-gl4.width)/2f, btnExo.y-14f);
        uiBatch.end();
    }

    private void renderExoMenu(){
        ScreenUtils.clear(0.02f,0.02f,0.05f,1);
        shapeRenderer.setProjectionMatrix(uiCam.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f,1f,1f,0.65f);
        for (Vector2 s:starfield) shapeRenderer.circle(s.x,s.y,1.0f);
        shapeRenderer.end();

        uiBatch.setProjectionMatrix(uiCam.combined); uiBatch.begin();
        String title="Exo Planets — Select a Model";
        GlyphLayout tl=new GlyphLayout(titleFont,title);
        float tx=(uiCam.viewportWidth-tl.width)/2f, ty=uiCam.viewportHeight*0.80f;
        titleFont.setColor(1f,0.95f,0.8f,1f); titleFont.draw(uiBatch, tl, tx, ty);
        if (exoEntries.size==0){
            String msg="No exo models found in resources: models/exo/*.json";
            GlyphLayout g=new GlyphLayout(font,msg);
            font.draw(uiBatch, g, (uiCam.viewportWidth-g.width)/2f, ty-40f);
        } else {
            ExoEntry e = exoEntries.get(exoIndex);
            GlyphLayout g1=new GlyphLayout(font, e.name);
            font.setColor(1f,1f,1f,1f);
            font.draw(uiBatch, g1, (uiCam.viewportWidth-g1.width)/2f, uiCam.viewportHeight*0.60f);
            layout.setText(font, e.description, Color.WHITE, uiCam.viewportWidth*0.70f, 1, true);
            font.draw(uiBatch, layout, (uiCam.viewportWidth-layout.width)/2f, uiCam.viewportHeight*0.52f);
        }
        uiBatch.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND); Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setColor(hoveredPrev?0.25f:0.18f,0.18f,0.18f,0.9f); shapeRenderer.rect(btnPrev.x,btnPrev.y,btnPrev.width,btnPrev.height);
        shapeRenderer.setColor(hoveredNext?0.25f:0.18f,0.18f,0.18f,0.9f); shapeRenderer.rect(btnNext.x,btnNext.y,btnNext.width,btnNext.height);
        shapeRenderer.setColor(hoveredLoad?0.35f:0.22f,0.45f,0.8f,0.95f); shapeRenderer.rect(btnLoad.x,btnLoad.y,btnLoad.width,btnLoad.height);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.95f,0.98f,1f,1f);
        shapeRenderer.rect(btnPrev.x,btnPrev.y,btnPrev.width,btnPrev.height);
        shapeRenderer.rect(btnNext.x,btnNext.y,btnNext.width,btnNext.height);
        shapeRenderer.rect(btnLoad.x,btnLoad.y,btnLoad.width,btnLoad.height);
        shapeRenderer.end();

        uiBatch.begin();
        GlyphLayout glPrev=new GlyphLayout(font,"< Prev");
        font.draw(uiBatch, glPrev, btnPrev.x+(btnPrev.width-glPrev.width)/2f, btnPrev.y+(btnPrev.height+glPrev.height)/2f);
        GlyphLayout glNext=new GlyphLayout(font,"Next >");
        font.draw(uiBatch, glNext, btnNext.x+(btnNext.width-glNext.width)/2f, btnNext.y+(btnNext.height+glNext.height)/2f);
        GlyphLayout glLoad=new GlyphLayout(font,"Load Model");
        font.draw(uiBatch, glLoad, btnLoad.x+(btnLoad.width-glLoad.width)/2f, btnLoad.y+(btnLoad.height+glLoad.height)/2f);
        uiBatch.end();
    }

    @Override public void resize(int w,int h){
        camera.viewportWidth=w; camera.viewportHeight=h; camera.update();
        uiCam.setToOrtho(false,w,h); uiCam.update();
        updateSplashButtons(); updateExoButtons();
    }

    @Override public void dispose(){
        modelBatch.dispose(); shapeRenderer.dispose(); uiBatch.dispose();
        font.dispose(); titleFont.dispose();
        if (sunModel!=null) sunModel.dispose();
        if (planetModel!=null) planetModel.dispose();
        if (moonModel!=null) moonModel.dispose();
        if (saturnRingModel!=null) saturnRingModel.dispose();
        if (txSun!=null) txSun.dispose();
        if (txSunHalo!=null) txSunHalo.dispose();
        if (txSaturnRings!=null) txSaturnRings.dispose();
    }

    // ----- Camera helpers -----
    private void getCameraBasis(Vector3 outRight, Vector3 outUp, Vector3 outForward){
        float yaw=camYawDeg*MathUtils.degreesToRadians, pitch=camPitchDeg*MathUtils.degreesToRadians;
        outForward.set(-MathUtils.cos(pitch)*MathUtils.cos(yaw), -MathUtils.sin(pitch), -MathUtils.cos(pitch)*MathUtils.sin(yaw)).nor();
        outRight.set(outForward.z,0,-outForward.x).nor();
        outUp.set(outRight).crs(outForward).nor();
    }
    private void updateCamera(){
        float yaw=camYawDeg*MathUtils.degreesToRadians, pitch=camPitchDeg*MathUtils.degreesToRadians;
        float cx=camTarget.x + camDistance*MathUtils.cos(pitch)*MathUtils.cos(yaw);
        float cz=camTarget.z + camDistance*MathUtils.cos(pitch)*MathUtils.sin(yaw);
        float cy=camTarget.y + camDistance*MathUtils.sin(pitch);
        if (camera==null) camera=new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(cx,cy,cz); camera.up.set(0,1,0); camera.lookAt(camTarget); camera.near=0.1f; camera.far=4000f; camera.update();
    }
    private void resetCamera(){ camDistance=defaultCamDistance; camYawDeg=defaultCamYawDeg; camPitchDeg=defaultCamPitchDeg; camTarget.set(defaultCamTarget); rightDragging=false; middleDragging=false; updateCamera(); }
    private void focusOnPlanet(int idx){ for (Body p:planets) if (p.index==idx){ camTarget.set(p.position); camDistance=Math.max(8f, p.visualScale*6f); updateCamera(); break; } }

    private void updateSplashButtons(){
        float w=uiCam.viewportWidth, h=uiCam.viewportHeight;
        float btnW=360f, btnH=56f, spacing=16f, x=(w-btnW)/2f, base=h*0.40f;
        btnStatic.set(x, base + (btnH+spacing)*2f, btnW, btnH);
        btnDynamic.set(x, base + (btnH+spacing)*1f, btnW, btnH);
        btnExo.set(x, base, btnW, btnH);
    }
    private void updateExoButtons(){
        float w=uiCam.viewportWidth, h=uiCam.viewportHeight;
        float btnW=120f, btnH=48f;
        btnPrev.set(w*0.15f - btnW/2f, h*0.35f - btnH/2f, btnW, btnH);
        btnNext.set(w*0.85f - btnW/2f, h*0.35f - btnH/2f, btnW, btnH);
        btnLoad.set((w-200f)/2f, h*0.22f - btnH/2f, 200f, btnH);
    }

    // Toggleable extended lighting without touching internal fields
    private void applyLightingProfile() {
        // Ambient
        ColorAttribute amb = (ColorAttribute) environment.get(ColorAttribute.AmbientLight);
        if (amb != null) {
            if (extendedLighting) amb.color.set(0.22f, 0.22f, 0.26f, 1f);
            else                  amb.color.set(0.16f, 0.16f, 0.20f, 1f);
        }

        // Sun intensity/color remains from model; we can mildly boost when extended
        if (sunPoint != null) {
            float i = extendedLighting ? 2600f : 1400f;
            sunPoint.intensity = i;
        }

        // Rebuild directionals using public API
        environment.remove(fillWarm);
        environment.remove(fillCool);
        if (extendedLighting) {
            environment.add(fillWarm);
            environment.add(fillCool);
        }
    }

    private void applyMoonMode() {
        for (Moon m : moons) {
            if (moonMode == MoonMode.HIDDEN) {
                m.visible = false;
            } else {
                m.visible = true;
                float scale = (moonMode == MoonMode.EXAGGERATED) ? m.exaggeratedScale : m.realisticScale;
                m.setScaleAndRecompute(scale);
            }
        }
        // After changing moon scales (thus periapses), rebuild Saturn rings (clearance may change)
        rebuildSaturnRings();
    }


    // ----- Texture helpers -----
    private float hash2D(int x, int y){
        int h = x*374761393 + y*668265263; // large primes
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        // 0..1
        return (h & 0x7FFFFFFF) / 2147483647f;
    }
    private float smoothNoise(float x, float y){
        int xi = (int)Math.floor(x), yi = (int)Math.floor(y);
        float tx = x - xi, ty = y - yi;

        float a = hash2D(xi, yi);
        float b = hash2D(xi+1, yi);
        float c = hash2D(xi, yi+1);
        float d = hash2D(xi+1, yi+1);

        float sx = tx*tx*(3f-2f*tx);
        float sy = ty*ty*(3f-2f*ty);

        float u = MathUtils.lerp(a, b, sx);
        float v = MathUtils.lerp(c, d, sx);
        return MathUtils.lerp(u, v, sy);
    }

    private Texture makeRadialSunTexture(int w,int h, Color inner, Color outer){
        Pixmap pm=new Pixmap(w,h, Pixmap.Format.RGBA8888);
        float cx=w/2f, cy=h/2f, maxR=Math.max(w,h)/2f;
        for (int y=0;y<h;y++) for (int x=0;x<w;x++){
            float dx=x-cx, dy=y-cy, r=(float)Math.sqrt(dx*dx+dy*dy)/maxR, t=MathUtils.clamp(r,0f,1f);
            float rr=MathUtils.lerp(inner.r,outer.r,t), gg=MathUtils.lerp(inner.g,outer.g,t), bb=MathUtils.lerp(inner.b,outer.b,t);
            pm.drawPixel(x,y, Color.rgba8888(rr,gg,bb,1f));
        }
        Texture tx=new Texture(pm); tx.setFilter(TextureFilter.Linear, TextureFilter.Linear); pm.dispose(); return tx;
    }
    private Texture makeSunHaloTexture(int size, Color color, float innerAlpha, float outerAlpha){
        Pixmap pm=new Pixmap(size,size, Pixmap.Format.RGBA8888);
        float cx=size*0.5f, cy=size*0.5f, maxR=size*0.5f;
        for (int y=0;y<size;y++) for (int x=0;x<size;x++){
            float dx=x-cx, dy=y-cy, r=(float)Math.sqrt(dx*dx+dy*dy)/maxR, t=MathUtils.clamp(r,0f,1f);
            float a=MathUtils.lerp(innerAlpha, outerAlpha, t*t*(3f-2f*t));
            pm.drawPixel(x,y, Color.rgba8888(color.r,color.g,color.b,a));
        }
        Texture tx=new Texture(pm); tx.setFilter(TextureFilter.Linear, TextureFilter.Linear); pm.dispose(); return tx;
    }
    private Texture makeSpeckleTexture(int w,int h, Color base, Color speck,float density){
        Pixmap pm=new Pixmap(w,h, Pixmap.Format.RGBA8888);
        pm.setColor(base); pm.fill(); pm.setColor(speck);
        int count=(int)(w*h*density);
        for (int i=0;i<count;i++){ int x=MathUtils.random(0,w-1), y=MathUtils.random(0,h-1); pm.drawPixel(x,y); }
        Texture tx=new Texture(pm); tx.setFilter(TextureFilter.Linear, TextureFilter.Linear); pm.dispose(); return tx;
    }
    private Texture makeBandedTexture(int w,int h, Color[] bands, float jitter){
        Pixmap pm=new Pixmap(w,h, Pixmap.Format.RGBA8888);
        int nb=Math.max(2, bands.length);

        // Frequency knobs
        float latNoiseFreq = 6.0f;     // band waviness
        float lonStreakFreq = 18.0f;   // faint longitudinal streaks
        float turbAmount    = 0.15f;   // turbulence depth

        for (int y=0; y<h; y++){
            float t = y/(float)(h-1);          // 0..1 from pole to pole
            float pos = t * nb;                // which band
            int i = Math.min(nb-1, (int)pos);
            float frac = pos - i;

            // Band jitter by latitudinal noise
            float n0 = smoothNoise(t*latNoiseFreq, 0.0f);
            float n1 = smoothNoise(t*latNoiseFreq*2f, 3.3f);
            float n2 = smoothNoise(t*latNoiseFreq*4f, 7.7f);
            float turb = (n0*0.6f + n1*0.3f + n2*0.1f) - 0.5f;
            float f = MathUtils.clamp(frac + turb*jitter*1.2f, 0f, 1f);

            Color c0 = bands[i];
            Color c1 = bands[Math.min(nb-1, i+1)];
            float r = MathUtils.lerp(c0.r,c1.r,f);
            float g = MathUtils.lerp(c0.g,c1.g,f);
            float b = MathUtils.lerp(c0.b,c1.b,f);

            // Longitudinal streaks (very subtle)
            for (int x=0; x<w; x++){
                float u = x/(float)(w-1);
                float s0 = smoothNoise(u*lonStreakFreq, t*lonStreakFreq);
                float s1 = smoothNoise(u*lonStreakFreq*2f, t*lonStreakFreq*0.8f);
                float streak = (s0*0.7f + s1*0.3f - 0.5f) * 0.08f;
                float rr = MathUtils.clamp(r + streak, 0f, 1f);
                float gg = MathUtils.clamp(g + streak, 0f, 1f);
                float bb = MathUtils.clamp(b + streak*0.6f, 0f, 1f);
                pm.drawPixel(x,y, Color.rgba8888(rr,gg,bb,1f));
            }
        }
        Texture tx=new Texture(pm); tx.setFilter(TextureFilter.Linear, TextureFilter.Linear); pm.dispose(); return tx;
    }


    // ----- Rings -----
    private Model createRingModel(float innerR,float outerR,int segments,Material material){
        ModelBuilder mb=new ModelBuilder(); mb.begin();
        long attrs=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.TextureCoordinates;
        MeshPartBuilder mpb=mb.part("saturn_rings", GL20.GL_TRIANGLES, attrs, material);
        Vector3 n=new Vector3(0f,1f,0f); float d=MathUtils.PI2/segments;
        for (int i=0;i<segments;i++){
            float a0=i*d, a1=(i+1)*d;
            float c0=MathUtils.cos(a0), s0=MathUtils.sin(a0), c1=MathUtils.cos(a1), s1=MathUtils.sin(a1);
            MeshPartBuilder.VertexInfo i0=new MeshPartBuilder.VertexInfo().setPos(innerR*c0,0f,innerR*s0).setNor(n).setUV(0f,(a0/MathUtils.PI2));
            MeshPartBuilder.VertexInfo i1=new MeshPartBuilder.VertexInfo().setPos(innerR*c1,0f,innerR*s1).setNor(n).setUV(0f,(a1/MathUtils.PI2));
            MeshPartBuilder.VertexInfo o0=new MeshPartBuilder.VertexInfo().setPos(outerR*c0,0f,outerR*s0).setNor(n).setUV(1f,(a0/MathUtils.PI2));
            MeshPartBuilder.VertexInfo o1=new MeshPartBuilder.VertexInfo().setPos(outerR*c1,0f,outerR*s1).setNor(n).setUV(1f,(a1/MathUtils.PI2));
            mpb.triangle(i0,i1,o1); mpb.triangle(i0,o1,o0);
        }
        return mb.end();
    }
    private Texture makeSaturnRingTexture(int w,int h){
        Pixmap pm=new Pixmap(w,h, Pixmap.Format.RGBA8888);
        for (int x=0;x<w;x++){
            float u=x/(float)(w-1);
            float r=MathUtils.lerp(0.88f,0.96f,u), g=MathUtils.lerp(0.82f,0.92f,u), b=MathUtils.lerp(0.70f,0.86f,u);
            float alpha=smoothstep(0.05f,0.95f,u);
            float bands=(MathUtils.sin(u*70f)*0.5f+0.5f)*0.12f + (MathUtils.sin(u*14f)*0.5f+0.5f)*0.08f;
            r*=(1f-bands*0.6f); g*=(1f-bands*0.6f); b*=(1f-bands*0.6f);
            int rgba=Color.rgba8888(r,g,b,alpha); for (int y=0;y<h;y++) pm.drawPixel(x,y,rgba);
        }
        Texture tx=new Texture(pm); tx.setFilter(TextureFilter.Linear, TextureFilter.Linear); pm.dispose(); return tx;
    }
    private float smoothstep(float e0,float e1,float x){ float t=MathUtils.clamp((x-e0)/(e1-e0),0f,1f); return t*t*(3f-2f*t); }

    // ----- JSON loading -----
    private void clearSystem(){ planets.clear(); moons.clear(); saturnBody=null; saturnRingInstance=null; }
    private void loadModelFromFile(String internalPath){
        try{
            FileHandle fh=Gdx.files.internal(internalPath); String json=fh.readString("UTF-8");
            loadModelFromJsonString(json);
        }catch(Exception e){ Gdx.app.error("Vibe","Failed to load "+internalPath, e); }
    }
    private void loadModelFromJsonString(String json){
        clearSystem();
        JsonValue root=new JsonReader().parse(json);

        modelDistanceScale = root.getFloat("distanceScale", 1.0f);
        planetSizeScale    = root.getFloat("planetSizeScale", 1.0f);
        moonExagScale      = root.getFloat("moonExaggeratedScale", 1.0f);
        moonRealScale      = root.getFloat("moonRealisticScale", 1.0f);

        truePlanetProportionsDefault = root.getBoolean("truePlanetProportions", false);
        trueMoonProportionsInRealisticDefault = root.getBoolean("trueMoonProportionsInRealistic", false);

        // apply defaults now (can be toggled at runtime)
        truePlanetProportions = truePlanetProportionsDefault;
        trueMoonProportionsInRealistic = trueMoonProportionsInRealisticDefault;


        // star
        JsonValue star=root.get("star");
        Color starColor=new Color(1f,0.95f,0.6f,1f), starEmiss=new Color(1f,0.92f,0.55f,1f);
        float starRadius=4f;
        if (star!=null){
            float[] c = getColorArray(star.get("color"), new float[]{1f,0.95f,0.6f,1f});
            float[] e = getColorArray(star.get("emissive"), new float[]{1f,0.92f,0.55f,1f});
            starColor.set(c[0],c[1],c[2], c.length>3?c[3]:1f);
            starEmiss.set(e[0],e[1],e[2], e.length>3?e[3]:1f);
            starRadius = star.getFloat("radiusVisual", 4f);
        }
        sunInstance = new ModelInstance(sunModel);
        sunInstance.transform.setToScaling(starRadius,starRadius,starRadius);
        for (Material m: sunInstance.materials){
            m.set(TextureAttribute.createDiffuse(txSun));
            m.set(ColorAttribute.createDiffuse(Color.WHITE));
            m.set(ColorAttribute.createEmissive(starEmiss));
        }
        environment.remove(sunPoint);
        sunPoint=new PointLight().set(starColor.r,starColor.g,starColor.b, 0,0,0, 2000f);
        environment.add(sunPoint);

        int nextIdx=1;
        for (JsonValue b : root.get("bodies")){
            String type=b.getString("type","planet");
            if (!"planet".equals(type)) continue;
            String name=b.getString("name","?");
            int index=b.getInt("index", nextIdx++);
            float aAU=b.getFloat("aAU",1f), e=b.getFloat("e",0f), period=b.getFloat("periodDays",365f);
            float radiusKm=b.getFloat("radiusKm",6371f);
            float inc=b.getFloat("inclinationDeg",0f), tilt=b.getFloat("spinTiltDeg",0f), rot=b.getFloat("rotationDays",0f);
            Color color = readColor(b.get("color"), new Color(1,1,1,1));
            float vis = planetVisualRadius(radiusKm);
            Body p=new Body(name,index,aAU,e,period,color,planetModel,vis,inc,tilt,rot);
            applyTextureFromJson(b.get("texture"), p);
            planets.add(p);

            // moons
            JsonValue ms=b.get("moons");
            if (ms!=null){
                for (JsonValue m : ms){
                    String mn=m.getString("name","m");
                    float ma=m.getFloat("aAU",0.002f), me=m.getFloat("e",0f), mp=m.getFloat("periodDays",5f);
                    int mr=m.getInt("radiusKm",1000);
                    float mi=m.getFloat("inclinationDeg",0f);
                    float ex=moonVisualRadiusExaggerated(mr), rl=moonVisualRadiusRealistic(mr);
                    Moon moon=new Moon(mn,p,ma,me,mp,Color.WHITE,moonModel,ex,rl,mi);
                    applyGenericMoonTexture(moon);
                    moons.add(moon);
                }
            }
        }

        // initial orbit polylines
        for (Body p : planets) p.initOrbit();
        for (Moon m : moons)  m.initOrbit();

        // enforce spacing with the sizes that are actually in use
        applyMoonMode(); // ensures moon scales match current mode
        for (Body p : planets) enforceMoonSeparation(p);

        rebuildSaturnRings();
        applyLightingProfile();
    }

    private void applyTextureFromJson(JsonValue txNode, Body p){
        if (txNode==null) return;
        String type=txNode.getString("type","speckle");
        if ("banded".equals(type)){
            JsonValue arr=txNode.get("bands"); float jitter=txNode.getFloat("jitter",0.06f);
            if (arr!=null && arr.size>0){
                Color[] bands=new Color[arr.size]; int i=0;
                for (JsonValue c:arr){ bands[i++]=new Color(c.getFloat(0),c.getFloat(1),c.getFloat(2),1f); }
                Texture t=makeBandedTexture(512,256,bands,jitter);
                for (Material m: p.instance.materials) m.set(TextureAttribute.createDiffuse(t));
            }
        } else {
            float[] a=getColorArray(txNode.get("colorA"), new float[]{0.8f,0.8f,0.85f,1f});
            float[] b=getColorArray(txNode.get("colorB"), new float[]{0.7f,0.7f,0.75f,1f});
            float density=txNode.getFloat("density",0.002f);
            Texture t=makeSpeckleTexture(512,256,new Color(a[0],a[1],a[2],1f), new Color(b[0],b[1],b[2],1f), density);
            for (Material m: p.instance.materials) m.set(TextureAttribute.createDiffuse(t));
        }
    }
    private void applyGenericMoonTexture(Moon m){
        Texture t=makeSpeckleTexture(512,256,new Color(0.8f,0.8f,0.85f,1f), new Color(0.7f,0.7f,0.75f,1f), 0.0025f);
        for (Material mat: m.instance.materials) mat.set(TextureAttribute.createDiffuse(t));
    }

    // ----- Sizes -----
    private float planetVisualRadius(float radiusKm){
        // If true proportions: visual radius ~ linear to actual radius, normalized by Earth
        if (truePlanetProportions) {
            return (float)(planetSizeScale * (radiusKm / 6371.0));
        }
        // Otherwise: perceptual (cube-root) mapping for legibility
        double earth = Math.cbrt(6371.0), r = Math.cbrt(radiusKm);
        return (float)(planetSizeScale * (1.2 * (r/earth)));
    }

    private float moonVisualRadiusExaggerated(float radiusKm){
        // Perceptual mapping for exag. mode (a bit smaller than before) + model multiplier
        double moon = Math.cbrt(1737.0), r = Math.cbrt(radiusKm);
        return (float)(moonExagScale * (0.60 * (r/moon)));
    }

    private float moonVisualRadiusRealistic(float radiusKm){
        // If true proportions in realistic mode: linear to actual radius, normalized by our Moon
        if (trueMoonProportionsInRealistic) {
            return (float)(moonRealScale * (radiusKm / 1737.0));
        }
        double moon = Math.cbrt(1737.0), r = Math.cbrt(radiusKm);
        return (float)(moonRealScale * (0.28 * (r/moon)));
    }

    private float currentMoonVisualRadius(Moon m){
        // Read current scale from the transform so it matches what’s on-screen
        Vector3 s = new Vector3(); m.instance.transform.getScale(s);
        return s.x; // uniform scale
    }


    private void enforceMoonSeparation(Body parent){
        Array<Moon> list = new Array<>();
        for (Moon m : moons) if (m.parent == parent && m.visible) list.add(m);
        if (list.size <= 1) return;

        list.sort((a,b) -> Float.compare(a.aEff, b.aEff));

        for (int i=1; i<list.size; i++){
            Moon inner = list.get(i-1);
            Moon outer = list.get(i);

            float rIn  = currentMoonVisualRadius(inner);
            float rOut = currentMoonVisualRadius(outer);
            float minGap = 1.5f * Math.max(rIn, rOut);   // your requested rule

            if (outer.aEff - inner.aEff < minGap){
                float needed = inner.aEff + minGap;
                float dx = needed - outer.aEff;
                outer.aEff += dx;
                outer.bEff = outer.aEff * (float)Math.sqrt(1f - outer.e*outer.e);
                outer.initOrbit();
            }
        }
    }



    // ----- Saturn rings (optional) -----
    private void rebuildSaturnRings(){
        saturnBody=null; for (Body p:planets) if ("Saturn".equals(p.name)) { saturnBody=p; break; }
        if (saturnBody==null) return;
        float inner=saturnBody.visualScale*1.25f, outer=saturnBody.visualScale*2.0f;
        float minMoonPeri=Float.POSITIVE_INFINITY;
        for (Moon m:moons) if (m.parent==saturnBody && m.visible) minMoonPeri=Math.min(minMoonPeri, m.getPeriapsisWorld());
        if (minMoonPeri<Float.POSITIVE_INFINITY){ outer=Math.min(outer, minMoonPeri*0.70f); inner=Math.min(inner, outer*0.75f); }

        Material ringMat;
        if (saturnRingInstance==null){
            if (txSaturnRings==null) txSaturnRings=makeSaturnRingTexture(1024,64);
            ringMat=new Material(new BlendingAttribute(true,1f), TextureAttribute.createDiffuse(txSaturnRings), ColorAttribute.createDiffuse(1f,1f,1f,1f), FloatAttribute.createShininess(8f));
        } else {
            ringMat=saturnRingInstance.materials.first(); if (saturnRingModel!=null) saturnRingModel.dispose();
        }
        saturnRingModel=createRingModel(inner,outer,128, ringMat); saturnRingInstance=new ModelInstance(saturnRingModel);
    }

    // ----- Utils -----
    private float[] getColorArray(JsonValue v,float[] def){ if (v==null||v.size<3) return def; float r=v.getFloat(0),g=v.getFloat(1),b=v.getFloat(2); float a=v.size>3?v.getFloat(3):1f; return new float[]{r,g,b,a}; }
    private Color readColor(JsonValue v,Color def){ if (v==null||v.size<3) return def; return new Color(v.getFloat(0),v.getFloat(1),v.getFloat(2), v.size>3?v.getFloat(3):1f); }
}
