package shivanhunter.voxelmodeller;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class MainScreen extends ApplicationAdapter implements InputProcessor {
	// camera control vars
	private PerspectiveCamera cam;
	private float camDistance = 10;
	private float xRot, yRot;
	
	// environment/rendering vars
	private Environment environment;
	private Color fogColor;
	
	// batch objects
	private SpriteBatch guiBatch;
	private ShapeRenderer colorBatch;
	private ModelBatch modelBatch;
	
	// model state
	private VoxelModel model;
	private ArrayList<Color> colors;
	
	// GUI stuff
	private Stage stage;
	private int selectedColor = 1, mouseOverColor;
	private Ray pickRay;
	private Vector2 mousePosition;
	private boolean movingCamera = false;

	// buttons for color menu control
	private Texture
		removeColor,
		addColor;
	
	/*
	 * TODO list:
	 * 
	 * implement changing size of model
	 * implement three independent dimensions for width, depth, height
	 * implement changing root location
	 * implement selecting colors
	 */

	/**
	 * Creates a new MainScreen object. Initializes the environment and models.
	 */
	@Override public void create() {
		initCamera();
		
		// set up batch objects
		modelBatch = new ModelBatch();
		guiBatch = new SpriteBatch();
		colorBatch = new ShapeRenderer();

		// set up environment
		fogColor = new Color(.15f, .2f, .25f, 1);
		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.Fog, fogColor));
		environment.add(new DirectionalLight().set(1f, 1f, 1f, -1f, -0.8f, -0.4f));
		environment.add(new DirectionalLight().set(0.2f, 0.2f, 0.25f, 1f, 0.8f, 0.4f));
		
		// initialize model
		model = new VoxelModel(8);
		
		// set up mouse input
		mousePosition = new Vector2();
		updatePickRay();
		
		// initialize color list and add a set of starting colors
		colors = new ArrayList<Color>();
		colors.add(new Color(0, 0, 0, 0));
		colors.add(Color.WHITE);
		colors.add(Color.LIGHT_GRAY);
		colors.add(Color.GRAY);
		colors.add(Color.DARK_GRAY);
		colors.add(Color.BLACK);
		
		colors.add(Color.RED);
		colors.add(Color.GREEN);
		colors.add(Color.BLUE);
		colors.add(Color.MAGENTA);
		colors.add(Color.YELLOW);
		colors.add(Color.CYAN);

		colors.add(Color.MAROON);
		colors.add(new Color(0, .5f, 0, 1)); // why no predefined dark green?
		colors.add(Color.NAVY);
		colors.add(Color.PURPLE);
		colors.add(Color.OLIVE);
		colors.add(Color.TEAL);
		
		colors.add(Color.PINK);
		colors.add(Color.ORANGE);
		
		// set up GUI
		stage = new Stage();
		
		// input that Stage doesn't handle is sent to this
		InputMultiplexer muxer = new InputMultiplexer();
		muxer.addProcessor(stage);
		muxer.addProcessor(this);
		Gdx.input.setInputProcessor(muxer);
		
		// get textures for buttons
		// TODO: elegantly fail in some way if these or other textures are not present
		removeColor = new Texture(Gdx.files.internal("removecolor.png"));
		addColor = new Texture(Gdx.files.internal("addcolor.png"));
		
		// load GUI buttons
		Button saveButton = newButton("save.png", "save_over.png", "save_down.png");
		Button loadButton = newButton("load.png", "load_over.png", "load_down.png");
		Button newButton = newButton("new.png", "new_over.png", "new_down.png");
		Button addxButton = newButton("root_addx.png", "root_addx_over.png", "root_addx_over.png");
		Button addyButton = newButton("root_addy.png", "root_addy_over.png", "root_addy_over.png");
		Button addzButton = newButton("root_addz.png", "root_addz_over.png", "root_addz_over.png");
		Button subxButton = newButton("root_subx.png", "root_subx_over.png", "root_subx_over.png");
		Button subyButton = newButton("root_suby.png", "root_suby_over.png", "root_suby_over.png");
		Button subzButton = newButton("root_subz.png", "root_subz_over.png", "root_subz_over.png");
		Button resetButton = newButton("root_reset.png", "root_reset_over.png", "root_reset_over.png");
		Button addsizeButton = newButton("size_add.png", "size_add_over.png", "size_add_over.png");
		Button subsizeButton = newButton("size_sub.png", "size_sub_over.png", "size_sub_over.png");
		
		// position buttons in top left corner
		int position = Gdx.graphics.getHeight() - 32;
		saveButton.moveBy(0, position); position -= 32;
		loadButton.moveBy(0, position); position -= 32;
		newButton.moveBy(0, position); position -= 32;
		addxButton.moveBy(0, position); position -= 32;
		addyButton.moveBy(0, position); position -= 32;
		addzButton.moveBy(0, position); position -= 32;
		subxButton.moveBy(0, position); position -= 32;
		subyButton.moveBy(0, position); position -= 32;
		subzButton.moveBy(0, position); position -= 32;
		resetButton.moveBy(0, position); position -= 32;
		addsizeButton.moveBy(0, position); position -= 32;
		subsizeButton.moveBy(0, position); position -= 32;
		
		// add functions when pressed
		saveButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        saveFile();
		    }
		});
		
		loadButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        loadFile();
		    }
		});
		
		newButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        newFile();
		    }
		});
		
		addxButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().x++; model.update();
		    }
		});
		
		addyButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().y++; model.update();
		    }
		});
		
		addzButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().z++; model.update();
		    }
		});
		
		subxButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().x--; model.update();
		    }
		});
		
		subyButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().y--; model.update();
		    }
		});
		
		subzButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().z--; model.update();
		    }
		});
		
		resetButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.getRootLocation().x = -model.getSize()/2f;
		        model.getRootLocation().y = -model.getSize()/2f;
		        model.getRootLocation().z = -model.getSize()/2f;
		        model.update();
		    }
		});
		
		addsizeButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.setSize(model.getSize() + 1);
		        model.update();
		    }
		});
		
		subsizeButton.addCaptureListener(new ChangeListener() {
		    public void changed (ChangeEvent event, Actor actor) {
		        model.setSize(model.getSize() - 1);
		        model.update();
		    }
		});
		
		// attach buttons to Stage
		stage.addActor(saveButton);
		stage.addActor(loadButton);
		stage.addActor(newButton);
		stage.addActor(addxButton);
		stage.addActor(addyButton);
		stage.addActor(addzButton);
		stage.addActor(subxButton);
		stage.addActor(subyButton);
		stage.addActor(subzButton);
		stage.addActor(resetButton);
		stage.addActor(addsizeButton);
		stage.addActor(subsizeButton);
	}
	
	/**
	 * Convenience method to create a new button given a set of filenames.
	 * TODO: elegantly fail somehow if textures aren't present
	 * 
	 * @param up image path for the normal state of the button
	 * @param mouseOver image path for the mouseover state of the button
	 * @param mouseDown image path for the pressed state of the button
	 * @return the new Button using the given images
	 */
	private Button newButton(String up, String mouseOver, String mouseDown) {
		ButtonStyle style = new ButtonStyle();
		style.up = getDrawable(up);
		style.over = getDrawable(mouseOver);
		style.down = getDrawable(mouseDown);
		
		return new Button(style);
	}
	
	/**
	 * Convenience function to get a LibGDX Drawable object from an image filename
	 * TODO: elegantly fail somehow if texture isn't present
	 * 
	 * @param texture the file path of the image to use
	 * @return the Drawable object
	 */
	private Drawable getDrawable(String texture) {
		Texture tex = new Texture(Gdx.files.internal(texture));
		return new TextureRegionDrawable(new TextureRegion(tex));
	}
	
	/**
	 * Opens a dialog to save the current model as a file.
	 */
	private void saveFile() {
		FileHandle toWrite = getFile(false);
		if (toWrite != null) {
			toWrite.delete();
			toWrite.writeBytes(model.serialize(), false);	
		}
	}
	
	/**
	 * Opens a dialog to load a file as the current model. Does not affect the
	 * current model if the file chosen is a corrupt/invalid model file.
	 */
	private void loadFile() {
		FileHandle toRead = getFile(true);
		if (toRead != null) {
			VoxelModel newModel = null;
			try {
				newModel = new VoxelModel(toRead.readBytes());
			}
			catch (IllegalArgumentException e) {
				System.err.println("Incorrectly formatted file: " + toRead.path() + ", loading failed.");
			}
			
			if (newModel != null) {
				model.dispose();
				model = newModel;
			}
		}
	}
	
	/**
	 * creates a new model as the current model.
	 */
	private void newFile() {
		model.dispose();
		model = new VoxelModel(model.getSize());
	}
	
	/**
	 * Gets a FileHandle for saving or loading using a JFileChooser. Returns
	 * null if the chooser throws a URISyntaxException or if the dialog is
	 * cancelled (shooser.getSelectedFile() returns null).\
	 * 
	 * @param open whether to open a file (if false, will create a save dialog)
	 * @return the chosen FileHandle or null
	 */
	private FileHandle getFile(boolean open) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter("Voxel model", "voxel"));
		try {
			chooser.setCurrentDirectory(new File(
					MainScreen.class.getClassLoader().getResource(".").toURI().getPath()));
		} catch (URISyntaxException e) { }
		if (open) {
			if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				return new FileHandle(chooser.getSelectedFile());
			}
		} else {
			if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
				return new FileHandle(chooser.getSelectedFile());
			}
		}
		return null;
	}
	
	/**
	 * Sets up the GUI batch objects and creates a PerspectiveCamera using the
	 * window's current width and height. Should be called on window resize.
	 */
	private void initCamera() {
		guiBatch = new SpriteBatch();
		colorBatch = new ShapeRenderer();
		
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(10f, 8f, 6f);
		cam.lookAt(0, 0, 0);
		cam.near = 1f;
		cam.far = 512f;
		cam.update();
	}
	
	/**
	 * Updates the camera's position based on the camDistance and rotation
	 * variables. Should be called whenever any of these change.
	 */
	private void updateCamera() {
		cam.position.set(
				camDistance*MathUtils.cos(yRot)*MathUtils.cos(xRot),
				camDistance*MathUtils.sin(yRot),
				camDistance*MathUtils.cos(yRot)*MathUtils.sin(xRot));
		cam.up.set(Vector3.Y);
		cam.lookAt(0, 0, 0);
		cam.update();
		updatePickRay();
	}
	
	/**
	 * Updates the pick ray based on the camera's position and mouse position.
	 * Should be called whenever tha mouse or camera is moved.
	 */
	private void updatePickRay() {
		pickRay = cam.getPickRay(mousePosition.x,  mousePosition.y);
	}
	
	/**
	 * Adds a color to the list of colors.
	 * 
	 * @param c the color to add
	 */
	public void addColor(Color c) {
		colors.add(c);
	}

	/**
	 * LibGDX method: renders the current scene. Draws the model and GUI.
	 */
	@Override public void render() {
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glClearColor(fogColor.r, fogColor.g, fogColor.b, fogColor.a);
		
        updateCamera();
		
		modelBatch.begin(cam);
		modelBatch.render(model.instance, environment);
		modelBatch.render(model.boundsInstance, environment);
		modelBatch.render(model.rootInstance, environment);
		modelBatch.end();

		// draw color array as a list of colors on the right side of the screen
		guiBatch.begin();
		int x = Gdx.graphics.getWidth(), y = Gdx.graphics.getHeight();
		for (int i = 0; i < colors.size(); ++i) {
			if (i > 1) guiBatch.draw(removeColor, x-32, y-16);
			y -= 16;
			if (y < 16) {
				x -= 32;
				y = Gdx.graphics.getHeight();
			}
		}
		guiBatch.draw(addColor, x-16, y-16);
		guiBatch.end();

		// draw filled color using a shapeRenderer
		colorBatch.begin(ShapeType.Filled);
		x = Gdx.graphics.getWidth();
		y = Gdx.graphics.getHeight();
		for (int i = 0; i < colors.size(); ++i) {
			colorBatch.setColor(colors.get(i));
			if (i != 0) colorBatch.rect(x-14, y-14, 12, 12);
			y -= 16;
			if (y < 16) {
				x -= 32;
				y = Gdx.graphics.getHeight();
			}
		}
		colorBatch.end();

		// draw outlines with brighter color for the selected color and mouseover color
		colorBatch.begin(ShapeType.Line);
		x = Gdx.graphics.getWidth();
		y = Gdx.graphics.getHeight();
		for (int i = 0; i < colors.size(); ++i) {
			if (i == selectedColor) colorBatch.setColor(Color.WHITE);
			else if (i == mouseOverColor) colorBatch.setColor(Color.LIGHT_GRAY);
			else colorBatch.setColor(Color.GRAY);
			colorBatch.rect(x-15, y-15, 14, 14);
			y -= 16;
			if (y < 16) {
				x -= 32;
				y = Gdx.graphics.getHeight();
			}
		}
		colorBatch.end();
		
		// update GUI elements
		stage.act(Gdx.graphics.getDeltaTime());
		stage.draw();
	}

	/**
	 * LibGDX method: called whenever the screen is resized
	 * recreate cameras and GUI Stage
	 * 
	 * @param width the new width of the screen in pixels
	 * @param height the new height of the screen in pixels
	 */
	@Override public void resize(int width, int height) {
		initCamera();
	    stage.getViewport().update(width, height, true);
	}
	
	/**
	 * Disposes any resources this screen uses that aren't handled by GC.
	 */
	@Override public void dispose () {
		modelBatch.dispose();
		guiBatch.dispose();
		colorBatch.dispose();
		stage.dispose();
		model.dispose();
	}

	/**
	 * LibGDX method: called whenever a touch event starts being registered
	 * 
	 * Only start dragging camera if the touch is not from button 0
	 * 
	 * @param screenX the touch's x position on the screen
	 * @param screenY the touch's y position on the screen
	 * @param pointer the index of the touch pointer for multi-touch devices
	 * @param button the index of the mouse button
	 */
	@Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		if (button != 0) movingCamera = true;
		return true;
	}

	/**
	 * LibGDX method: called whenever a touch event stops being registered
	 * 
	 * stop dragging camera, handle input
	 * 
	 * @param screenX the touch's x position on the screen
	 * @param screenY the touch's y position on the screen
	 * @param pointer the index of the touch pointer for multi-touch devices
	 * @param button the index of the mouse button
	 */
	@Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		if (button != 0) movingCamera = false;
		
		if (button == 0) {
			int x = Gdx.graphics.getWidth(), y = Gdx.graphics.getHeight();
			screenY = y - screenY;
			
			for (int i = 0; i < colors.size(); ++i) {
				if (screenY > y-16 && screenY < y) {
					if (screenX > x-32 && screenX < x-16) {
						if (i > 1) colors.remove(i);
						if (selectedColor == i) selectedColor = 1;
						return true;
					} else if (screenX > x-16 && screenX < x) {
						selectedColor = i;
						return true;
					}
				}
				
				y -= 16;
				if (y < 16) {
					x -= 32;
					y = Gdx.graphics.getHeight();
				}
			}
			
			if (screenX > x-16 && screenX < x) {
				colors.add(new Color(1, 1, 1, 1));
				return true;
			}
		}
		
		if (button == 0) model.modify(pickRay, colors.get(selectedColor));
		return true;
	}

	/**
	 * LibGDX method: called whenever the mouse moves while a button is held down
	 * 
	 * update mouse position and pickRay, move camera if applicable
	 * 
	 * @param screenX the touch's x position on the screen
	 * @param screenY the touch's y position on the screen
	 * @param pointer the index of the touch pointer for multi-touch devices
	 */
	@Override public boolean touchDragged(int screenX, int screenY, int pointer) {
		if (movingCamera) {
			int dx = (int)mousePosition.x - screenX;
			int dy = (int)mousePosition.y - screenY;
			
			xRot -= dx/100f;
			yRot -= dy/100f;
			yRot = MathUtils.clamp(yRot, -1.55f, 1.55f);
		}
			
		mousePosition.x = screenX;
		mousePosition.y = screenY;
		updatePickRay();
		return true;
	}

	/**
	 * LibGDX method: called whenever the mouse moves while no buttons are held down
	 * 
	 * update pickRay and mouseOver for color list
	 */
	@Override public boolean mouseMoved(int screenX, int screenY) {
		mousePosition.x = screenX;
		mousePosition.y = screenY;
		updatePickRay();
		
		int x = Gdx.graphics.getWidth(), y = Gdx.graphics.getHeight();
		screenY = y - screenY;
		for (int i = 0; i < colors.size(); ++i) {
			if (screenY > y-16 && screenY < y) {
				if (screenX > x-16 && screenX < x) {
					mouseOverColor = i;
					return true;
				}
			}
			
			y -= 16;
			if (y < 16) {
				x -= 32;
				y = Gdx.graphics.getHeight();
			}
		}
		
		mouseOverColor = -1;
		return true;
	}

	/**
	 * LibGDX function: called whenever the scroll wheel is scrolled
	 * 
	 * zooms the camera within a certain range
	 * 
	 * @param amount the scroll smount
	 */
	@Override public boolean scrolled(int amount) {
		if (amount < 0) {
			camDistance *= 0.8f;
		} else {
			camDistance *= 1.25f;
		}
		camDistance = MathUtils.clamp(camDistance, 4, 256);
		return true;
	}

	/*
	 * more LibGDX methods from InputProcessor and Screen, unused
	 */
	
	@Override public boolean keyDown(int keycode) { return false; }
	@Override public boolean keyUp(int keycode) { return false; }
	@Override public boolean keyTyped(char character) { return false; }

	@Override public void pause() { }
	@Override public void resume() { }
}
