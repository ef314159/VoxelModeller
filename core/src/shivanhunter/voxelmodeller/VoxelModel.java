package shivanhunter.voxelmodeller;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

public class VoxelModel {
	/*
	 * voxel data - bytes here are either 0 (no voxel) or an index into the
	 * color array (where a vluea of 3 is colors[2], and so on).
	 */
	private byte[][][] blocks;
	
	/*
	 * An array of no more than 255 colors for voxel indices
	 */
	private ArrayList<Color> colors;
	
	/*
	 * Cubic size of the voxel data
	 * (this may be split into width/height/depth later since flat models could
	 *  be storing many empty blocks)
	 */
	private int size;
	
	/*
	 * Scale of the model. Has no relevance to this modeller program, but
	 * affects how the model is drawn in a game world.
	 */
	private int scale;
	
	/*
	 * The model's root location. If a VoxelModel is drawn at (1, 1, 1), the
	 * rootLocation is the exact point that will be drawn at (1, 1, 1).
	 */
	private Vector3 rootLocation;
	
	// basic diffuse material for rendering voxels
	private Material mat;
	
	// the model representing the voxel data
	private Model model;
	public ModelInstance instance;
	
	// the wireframe box representing the bounds of the model
	private Model boundsModel;
	public ModelInstance boundsInstance;
	
	// the wireframe widget representing the root location
	private Model rootModel;
	public ModelInstance rootInstance;
	
	/*
	 * Axis is used to select an orthogonal direction in 3d space
	 */
	public enum Axis {
		POS_X,
		NEG_X,
		POS_Y,
		NEG_Y,
		POS_Z,
		NEG_Z
	}
	
	/**
	 * Constructs a VoxelModel using byte data from a file. Refer to
	 * voxel_spec.txt. Throws an IllegalArgumentException if the buffer's size
	 * does not match what is expected from the model's size and number of colors.
	 * 
	 * @param data the byte data from which to construct a model
	 */
	public VoxelModel(byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		byte version, num_materials;
		
		try {
			// first four bytes give basic info
			version = buffer.get(); // version unused since only version 0 exists
			num_materials = buffer.get();
			size = buffer.get()+1;
			scale = buffer.get()+1;
		} catch (BufferUnderflowException e) {
			throw new IllegalArgumentException();
		}
		
		// verify size
		if (buffer.capacity() != 16 + num_materials*12 + size*size*size) {
			throw new IllegalArgumentException();
		}
		
		// set up objects/lists
		mat = new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1));
		colors = new ArrayList<Color>();
		blocks = new byte[size][size][size];
		
		// next three floats are the root location
		rootLocation = new Vector3(
				buffer.getFloat(),
				buffer.getFloat(),
				buffer.getFloat());
		
		// next n*3 floats are the block colors
		for (int i = 0; i < num_materials; ++i) {
			colors.add(new Color(
					buffer.getFloat(),
					buffer.getFloat(),
					buffer.getFloat(),
					1));
		}
		
		// next n^3 bytes are the indices (block data)
		for (int i = 0; i < size; ++i) {
			for (int j = 0; j < size; ++j) {
				for (int k = 0; k < size; ++k) {
					blocks[i][j][k] = buffer.get();
				}
			}
		}

		// create model from loaded data
		updateBounds();
		updateMesh();
		updateRoot();
	}
	
	/**
	 * Creates a new VoxelModel with a given size. Initializes the model with a
	 * single white voxel near the center to build from.
	 * 
	 * @param size the cubic size of the model, in voxels
	 */
	public VoxelModel(int size) {
		// sanitize: size must be a byte value from 0-255
		// valid values are 1-256 since a size of 0 makes no sense
		if (size > 256) size = 256;
		if (size < 1) size = 1;
		this.size = size;
		
		// set up objects/lists
		mat = new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1));
		colors = new ArrayList<Color>();
		blocks = new byte[size][size][size];
		
		// initialize defaults:
		// root location in the exact middle
		rootLocation = new Vector3(-size/2f, -size/2f, -size/2f);
		
		// white color
		colors.add(new Color(1, 1, 1, 1));
		
		// a block near the middle using that color
		blocks[size/2][size/2][size/2] = 1;
		
		// create model from initial data
		updateBounds();
		updateMesh();
		updateRoot();
	}
	
	/**
	 * Deallocate LibGDX objects not handled by GC. Needs to be called on a
	 * VoxelModel before it is GC'd to prevent memory leak.
	 */
	public void dispose() {
		model.dispose();
		boundsModel.dispose();
		rootModel.dispose();
	}
	
	/**
	 * Updates the wireframe box representing the bounds of the voxel data.
	 * Should be called whenever the size or root location is changed.
	 */
	private void updateBounds() {
		ModelBuilder builder = new ModelBuilder();
		builder.begin();
		MeshPartBuilder partBuilder = builder.part("lines", GL20.GL_LINES, Usage.Position, mat);
		
		partBuilder.line(0,    0,    0,    0,    0,    size);
		partBuilder.line(0,    0,    0,    0,    size, 0);
		partBuilder.line(0,    0,    0,    size, 0,    0);
		partBuilder.line(0,    0,    size, 0,    size, size);
		partBuilder.line(0,    0,    size, size, 0,    size);
		partBuilder.line(0,    size, 0,    0,    size, size);
		partBuilder.line(0,    size, 0,    size, size, 0);
		partBuilder.line(size, 0,    0,    size, 0,    size);
		partBuilder.line(size, 0,    0,    size, size, 0);
		partBuilder.line(0,    size, size, size, size, size);
		partBuilder.line(size, 0,    size, size, size, size);
		partBuilder.line(size, size, 0,    size, size, size);
		
		boundsModel = builder.end();
		boundsInstance = new ModelInstance(boundsModel);
		boundsInstance.transform.translate(rootLocation);
	}
	
	/**
	 * Updates the wireframe widget representing the root location of the model.
	 * Should be called whenever the root location is changed.
	 */
	private void updateRoot() {
		ModelBuilder builder = new ModelBuilder();
		builder.begin();
		
		MeshPartBuilder partBuilder = builder.part(
				"lines", GL20.GL_LINES, Usage.Position, 
				new Material(ColorAttribute.createDiffuse(Color.BLUE)));
		partBuilder.line(0, 0, 0, 0, 0, 2);
		
		partBuilder = builder.part(
				"lines", GL20.GL_LINES, Usage.Position,
				new Material(ColorAttribute.createDiffuse(Color.GREEN)));
		partBuilder.line(0, 0, 0, 0, 2, 0);
		
		partBuilder = builder.part(
				"lines", GL20.GL_LINES, Usage.Position,
				new Material(ColorAttribute.createDiffuse(Color.RED)));
		partBuilder.line(0, 0, 0, 2, 0, 0);
		
		rootModel = builder.end();
		rootInstance = new ModelInstance(rootModel);
	}
	
	/**
	 * Updates the mesh representing the voxel data. Should be called whenever
	 * the size, root location or any blockdata is changed.
	 */
	private void updateMesh() {
		/* 
		 * Constants: number of verts and indices in a quad, number of floats
		 * in a vertex
		 */
        final int VERTS = 4, INDS = 6, FLOATS = 9;
        
        /*
         * Data in a vertex: 3 position floats, 3 color floats, 3 normal floats
         */
        VertexAttributes attributes = new VertexAttributes(
        		new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        		new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 3, "a_color"),
        		new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
        		);
        
        // count of quads to add
        int numQuads = 0;
        
        // temp list of vertices
        ArrayList<Float> verticesList = new ArrayList<Float>();
    	float r, g, b;
        
        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                for (int k = 0; k < size; ++k) {
                	
                	// only create quad facing outwards if there's a block at this cell
                	if (blocks[i][j][k] > 0) {
                		
                		// set color for any of this block's quads
                		r = colors.get(blocks[i][j][k]-1).r;
                		g = colors.get(blocks[i][j][k]-1).g;
                		b = colors.get(blocks[i][j][k]-1).b;
                		
                		// only add quads if the block they're facing towards is empty
                		if (i == 0 || blocks[i-1][j][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i,   j,   k+1,
                					i,   j+1, k+1,
                					i,   j+1, k,
                					r,   g,   b,  
                					Axis.NEG_X);
                			numQuads++;
                		}
                		if (i == size-1 || blocks[i+1][j][k] == 0) {
                			appendQuad(
                					verticesList,
                					i+1, j,   k,
                					i+1, j+1, k,
                					i+1, j+1, k+1,
                					i+1, j,   k+1,
                					r,   g,   b,
                					Axis.POS_X);
                			numQuads++;
                		}
                		if (j == 0 || blocks[i][j-1][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i+1, j,   k,
                					i+1, j,   k+1,
                					i,   j,   k+1,
                					r,   g,   b,
                					Axis.NEG_Y);
                			numQuads++;
                		}
                		if (j == size-1 || blocks[i][j+1][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j+1, k,
                					i,   j+1, k+1,
                					i+1, j+1, k+1,
                					i+1, j+1, k,
                					r,   g,   b,
                					Axis.POS_Y);
                			numQuads++;
                		}
                		if (k == 0 || blocks[i][j][k-1] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i,   j+1, k,
                					i+1, j+1, k,
                					i+1, j,   k,
                					r,   g,   b,
                					Axis.NEG_Z);
                			numQuads++;
                		}
                		if (k == size-1 || blocks[i][j][k+1] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k+1,
                					i+1, j,   k+1,
                					i+1, j+1, k+1,
                					i,   j+1, k+1,
                					r,   g,   b,
                					Axis.POS_Z);
                			numQuads++;
                		}
                	}
                }
            }
        }

        // create a mesh with room for the generated polygons
        Mesh mesh = new Mesh(true, numQuads*VERTS, numQuads*INDS, attributes);
        
        // create indices array
        short[] indices = new short[numQuads*INDS];
        
        // each polygon is 6 indices for each 4 vertices: two triangles
        // for each quad
        for (int i = 0; i < numQuads; ++i) {
        	indices[i*INDS + 0] = (short)(i*VERTS + 0);
        	indices[i*INDS + 1] = (short)(i*VERTS + 1);
        	indices[i*INDS + 2] = (short)(i*VERTS + 2);
        	
        	indices[i*INDS + 3] = (short)(i*VERTS + 2);
        	indices[i*INDS + 4] = (short)(i*VERTS + 3);
        	indices[i*INDS + 5] = (short)(i*VERTS + 0);
        }
        
        // convert list of verts to float[]
        float[] vertices = new float[numQuads*FLOATS*VERTS];
        
        // a list of Float can't be converted directly to a float[] because java sucks
        // so iterate through
        for (int i = 0; i < verticesList.size(); ++i) {
        	vertices[i] = verticesList.get(i);
        }

        // put generated lists in mesh
		mesh.setVertices(vertices);
		mesh.setIndices(indices);
		
		// build a LibGDX Model using mesh and material
		ModelBuilder builder = new ModelBuilder();
		builder.begin();
		builder.part("", mesh, GL20.GL_TRIANGLES, mat);
		model = builder.end();
		
		// create an instance to be rendered
		instance = new ModelInstance(model);
		instance.transform.translate(rootLocation);
	}
	
	/*
	 * AMBIENT OCCLUSION:
	 * 
	 * AO in a voxel model works by darkening cartain vertices based on the
	 * arrangement of the vaces around them. Refer to
	 *  
	 * http://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
	 * 
	 * for details.
	 * 
	 * This algorithm uses the proportion of opaque cells around a vertex to
	 * generate a darkening factor which is multiplied to the vertex color.
	 * This proportion is based on the cells in a cubic area around the vertex,
	 * but only in a given direction - if the face normal points toward the
	 * negative X axis, only cells with x coordinates less than the vertex's
	 * coordinate will be counted. The total area of cells checked for
	 * opaqueness is radius^3 / 2.
	 * 
	 * Since large radii lead to less detail around sharp edges, the algorithm
	 * works recursively, further darkening the cells by using lower radii as
	 * well. Therefore, a radius of 2 will lead to a total of
	 * (4*4*4/2) + (2*2*2/2) = 40 cells being checked.
	 */
	
	// The radius to use for ambient occlusion
	private static final int AO_Quality = 3;
	
	/**
	 * Gets a value from 0 to 1 representing ambient occlusion for the vertex
	 * at the given lcoation, using the given normal axis. The returned value
	 * if to be multiplied by the vertex color: a value of 1 means no darkening.
	 * This method calls the recursive method using AO_Quality as a radius.
	 * 
	 * @param x the vertex location in x
	 * @param y the vertex location in y
	 * @param z the vertex location in z
	 * @param axis the normal axis
	 * @return the lightness of the AO at the given point
	 */
	private float getAmbientOcclusion(int x, int y, int z, Axis axis) {
		return getAmbientOcclusion(x, y, z, axis, AO_Quality);
	}
	
	/**
	 * Recursive method used to get AO within a given radius.
	 * 
	 * @param x the vertex location in x
	 * @param y the vertex location in y
	 * @param z the vertex location in z
	 * @param axis the normal axis
	 * @param radius the radius of blocks to check for opaqueness
	 * @return the lightness of the AO at the given point
	 */
	private float getAmbientOcclusion(int x, int y, int z, Axis axis, int radius) {
		// base case - no darkening
		if (radius < 1) return 1;
		
		// count of opaque cells
		int cells = 0;
		
		// start and end values based on radius
		int startX = -radius,
				endX = radius,
				startY = -radius,
				endY = radius,
				startZ = -radius,
				endZ = radius;
		
		// cut one of them short based on which direction is being checked
		switch (axis) {
			case NEG_X: endX = 0; break;
			case NEG_Y: endY = 0; break;
			case NEG_Z: endZ = 0; break;
			case POS_X: startX = 0; break;
			case POS_Y: startY = 0; break;
			case POS_Z: startZ = 0; break;
		}
		
		// count up opaque cells
		for (int i = startX; i < endX; ++i) {
			for (int j = startY; j < endY; ++j) {
				for (int k = startZ; k < endZ; ++k) {
					if (x+i >= 0 && x+i < size &&
							y+j >= 0 && y+j < size &&
							z+k >= 0 && z+k < size &&
							blocks[x+i][y+j][z+k] > 0) cells++;
				}
			}
		}
		
		// number of opaque cells out of the maximum
		float proportion = (float)(cells/((Math.pow(radius*2, 3)/2)));
		
		// light amount is the inverse of this proportion
		float ao =  1-proportion;
		
		// recurse to get better results in tight corners
		return ao * (getAmbientOcclusion(x, y, z, axis, radius-1)+.1f)/1.1f;
	}
	
	/**
	 * Adds a quad to the VertexList.
	 * 
	 * @param vertexList the list under construction
	 * @param x1 the x coordinate of the first vertex in counterclockwise order
	 * @param y1 the y coordinate of the first vertex in counterclockwise order
	 * @param z1 the z coordinate of the first vertex in counterclockwise order
	 * @param x2 the x coordinate of the second vertex in counterclockwise order
	 * @param y2 the y coordinate of the second vertex in counterclockwise order
	 * @param z2 the z coordinate of the second vertex in counterclockwise order
	 * @param x3 the x coordinate of the third vertex in counterclockwise order
	 * @param y3 the y coordinate of the third vertex in counterclockwise order
	 * @param z3 the z coordinate of the third vertex in counterclockwise order
	 * @param x4 the x coordinate of the fourth vertex in counterclockwise order
	 * @param y4 the y coordinate of the fourth vertex in counterclockwise order
	 * @param z4 the z coordinate of the fourth vertex in counterclockwise order
	 * @param r the red channel of the vertex color
	 * @param g the green channel of the vertex color
	 * @param b the blue channel of the vertex color
	 * @param axis the axis of the quad normal
	 */
	public void appendQuad(
			ArrayList<Float> vertexList, 
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b,
			Axis axis) {
		
		// calculate ambient occlusion for each vertex
		float ambientOcclusion1 = getAmbientOcclusion((int)x1, (int)y1, (int)z1, axis);
		float ambientOcclusion2 = getAmbientOcclusion((int)x2, (int)y2, (int)z2, axis);
		float ambientOcclusion3 = getAmbientOcclusion((int)x3, (int)y3, (int)z3, axis);
		float ambientOcclusion4 = getAmbientOcclusion((int)x4, (int)y4, (int)z4, axis);
		
		// flip quad if necessary because of ambient occlusion
		// see "details regarding meshing":
		// http://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
		boolean flipped = (
				ambientOcclusion1 + ambientOcclusion3 < 
				ambientOcclusion2 + ambientOcclusion4);
		
		// convert normal axis enum to xyz vector
		// vector components will be 0 except for the axis along which the normal points
		float nx = 0, ny = 0, nz = 0;
		switch(axis) {
			case NEG_X: nx = -1; break;
			case NEG_Y: ny = -1; break;
			case NEG_Z: nz = -1; break;
			case POS_X: nx = 1; break;
			case POS_Y: ny = 1; break;
			case POS_Z: nz = 1; break;
		}
		
		// add the first vertex first if quad is not flipped
		if (!flipped) {
			vertexList.add(x1);
			vertexList.add(y1);
			vertexList.add(z1);
			vertexList.add(r*ambientOcclusion1);
			vertexList.add(g*ambientOcclusion1);
			vertexList.add(b*ambientOcclusion1);
			vertexList.add(nx);
			vertexList.add(ny);
			vertexList.add(nz);
		}
		
		vertexList.add(x2);
		vertexList.add(y2);
		vertexList.add(z2);
		vertexList.add(r*ambientOcclusion2);
		vertexList.add(g*ambientOcclusion2);
		vertexList.add(b*ambientOcclusion2);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);
		
		vertexList.add(x3);
		vertexList.add(y3);
		vertexList.add(z3);
		vertexList.add(r*ambientOcclusion3);
		vertexList.add(g*ambientOcclusion3);
		vertexList.add(b*ambientOcclusion3);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);
		
		vertexList.add(x4);
		vertexList.add(y4);
		vertexList.add(z4);
		vertexList.add(r*ambientOcclusion4);
		vertexList.add(g*ambientOcclusion4);
		vertexList.add(b*ambientOcclusion4);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);
		
		// add first vertex last if quad is flipped
		if (flipped) {
			vertexList.add(x1);
			vertexList.add(y1);
			vertexList.add(z1);
			vertexList.add(r*ambientOcclusion1);
			vertexList.add(g*ambientOcclusion1);
			vertexList.add(b*ambientOcclusion1);
			vertexList.add(nx);
			vertexList.add(ny);
			vertexList.add(nz);
		}
	}
	
	/**
	 * Serializes the model. For fotmat information refer to voxel_spec.txt
	 * size in bytes = 15 + 12*materials + size*size*size
	 * 
	 * @return the model in serialized format
	 */
	public byte[] serialize() {
		ByteBuffer buffer = ByteBuffer.allocate(16 + 12*colors.size() + size*size*size);
		
		buffer.put((byte)0); // version
		buffer.put((byte)colors.size()); // number of materials
		buffer.put((byte)(size-1)); // model size
		buffer.put((byte)(scale-1)); // model scale
		
		buffer.putFloat(rootLocation.x);
		buffer.putFloat(rootLocation.y);
		buffer.putFloat(rootLocation.z);
		
		for (Color c : colors) {
			buffer.putFloat(c.r);
			buffer.putFloat(c.g);
			buffer.putFloat(c.b);
		}
		
		for (int i = 0; i < size; ++i) {
			for (int j = 0; j < size; ++j) {
				for (int k = 0; k < size; ++k) {
					buffer.put(blocks[i][j][k]);
				}
			}
		}
		
		return buffer.array();
	}
	
	/**
	 * Sets the scale of the model. Valid values are 1 to 256.
	 * 
	 * @param newScale the desired scale of the model
	 */
	public void setScale(int newScale) {
		if (newScale < 1) newScale = 1;
		if (newScale > 256) newScale = 256;
		this.scale = newScale;
	}
	
	/**
	 * Sets the size of the model and rebuilds the array as an array of the
	 * new size. Valid values for size are 1 to 256.
	 * 
	 * @param newSize the desired size of the array
	 */
	public void setSize(int newSize) {
		if (newSize < 1) newSize = 1;
		if (newSize > 256) newSize = 256;
		
		byte[][][] newBlocks = new byte[newSize][newSize][newSize];
		int offset = (size-newSize)/2;
		for (int i = 0; i < size; ++i) {
			for (int j = 0; j < size; ++j) {
				for (int k = 0; k < size; ++k) {
					if (i+offset >= 0 && i+offset < newSize &&
							j+offset >= 0 && j+offset < newSize &&
							k+offset >= 0 && k+offset < newSize) {
						newBlocks[i+offset][j+offset][k+offset] = blocks[i][j][k];
					}
				}
			}
		}
		
		blocks = newBlocks;
		size = newSize;
		
		updateMesh();
		updateBounds();
	}
	
	/**
	 * Returns the size of the model.
	 * @return the size of the model
	 */
	public int getSize() {
		return size;
	}
	
	/**
	 * Returns the root lcoation of the model.
	 * 
	 * @return the root lcoation of the model
	 */
	public Vector3 getRootLocation() {
		return rootLocation;
	}
	
	/**
	 * Sets the root location of the model.
	 * @param newRootLocation the desired root location of the model
	 */
	public void setRootLocation(Vector3 newRootLocation) {
		this.rootLocation = newRootLocation;
		updateRoot();
	}

	/**
	 * Uses a pickRay and a Color to modify the model's voxel data. The PickRay
	 * tests collision against the voxel data and stores the nearest voxel to
	 * collide with it, and the axis representing the face the ray collided
	 * with.
	 * 
	 * If the Color's alpha channel is 0 (transparent), the collided voxel is
	 * removed; otherwise, a new voxel is added with the given color on the
	 * other side of the face the ray collided with.
	 * 
	 * @param pickRay the ray representing the pick
	 * @param toAdd the color to add
	 */
	public void modify(Ray pickRay, Color toAdd) {
		// analyze given color
		boolean remove = false;
		byte index = 0;
		
		// if transparent, remove rather than add a new color
		if (toAdd.a == 0) remove = true;
		// else, try to find the given color in the existing list
		else {
			for (int i = 0; i < colors.size(); ++i) {
				if (toAdd.equals(colors.get(i))) index = (byte)(i+1);
			}

			// if not found, add a new color
			if (index == 0) {
				// if the color list can't support a new color, return
				if (colors.size() >= 254) return;
				
				index = (byte)(colors.size()+1);
				colors.add(toAdd);
			}
		}
		
		// test hit
		RayHit hit = null;
		int x = -1, y = -1, z = -1;
		
		for (int i = 0; i < size; ++i) {
			for (int j = 0; j < size; ++j) {
				for (int k = 0; k < size; ++k) {
					// don't hit test against empty blocks
					if (blocks[i][j][k] == 0) continue;
					
					// get result of hit test
					RayHit tempHit = hitTest(pickRay,
							i+rootLocation.x, j+rootLocation.y, k+rootLocation.z,
							i+rootLocation.x+1, j+rootLocation.y+1, k+rootLocation.z+1);
					
					// if there was a hit, and it's closer than any existing hit
					if (tempHit != null && (hit == null ||
							tempHit.hitPoint.dst(pickRay.origin) < hit.hitPoint.dst(pickRay.origin))) {
						
						// store this hit
						hit = tempHit;
						x = i;
						y = j;
						z = k;
					}
				}
			}
		}
		
		// if a nearest hit exists
		if (hit != null) {
			// do stuff
			if (remove) {
				blocks[x][y][z] = 0;
			} else {
				switch(hit.axis) {
					case NEG_X: if (x > 0)      blocks[x-1][y][z] = index; break;
					case NEG_Y: if (y > 0)      blocks[x][y-1][z] = index; break;
					case NEG_Z: if (z > 0)      blocks[x][y][z-1] = index; break;
					case POS_X: if (x < size-1) blocks[x+1][y][z] = index; break;
					case POS_Y: if (y < size-1) blocks[x][y+1][z] = index; break;
					case POS_Z: if (z < size-1) blocks[x][y][z+1] = index; break;
				}
			}
			updateMesh();
		}
	}
	
	/**
	 * Find where the given pickRay intersects a given voxel. Returns a RayHit
	 * object representing the hit. If the ray intersects the voxel, the object
	 * will contain the precise location of the hit and the axis of the nearest
	 * face that intersects the ray. If the ray does not intersect the voxel,
	 * the object will be null.
	 *  
	 * @param pickRay The ray to test against
	 * @param x1 the lower x coordinate of the voxel
	 * @param y1 the lower y coordinate of the voxel
	 * @param z1 the lower z coordinate of the voxel
	 * @param x2 the higher x coordinate of the voxel
	 * @param y2 the higher y coordinate of the voxel
	 * @param z2 the higher z coordinate of the voxel
	 * @return a RayHit object or null
	 */
	private RayHit hitTest(Ray pickRay, float x1, float y1, float z1, float x2, float y2, float z2) {
		// get distances from the camera to each face
		float   dx1 = Math.abs(x1 - pickRay.origin.x),
				dx2 = Math.abs(x2 - pickRay.origin.x),
				dy1 = Math.abs(y1 - pickRay.origin.y),
				dy2 = Math.abs(y2 - pickRay.origin.y),
				dz1 = Math.abs(z1 - pickRay.origin.z),
				dz2 = Math.abs(z2 - pickRay.origin.z);

		float hitDistance, hitX, hitY, hitZ;
		
		// test X first
		// test the nearer of the two block sides that are on the YZ plane
		if (dx1 < dx2) {
			// distance of the hit is the distance in x from the camera to the plane
			// divided by the x component of the pickRay's direction unit vector
			hitDistance = dx1/pickRay.direction.x;
			// extend the ray out to find the exact point of the hit
			hitX = x1;
			hitY = pickRay.origin.y + pickRay.direction.y*hitDistance;
			hitZ = pickRay.origin.z + pickRay.direction.z*hitDistance;
			// check if the hit's Y and Z are within the square defined by the
			// block's side, given by y1, y2, z1, and z2
			if (hitY >= y1 && hitY <= y2 && hitZ >= z1 && hitZ <= z2) {
				// returning immediately incorrectly handles some cases where
				// the camera is between y1 and y2, or z1 and z2
				// TODO: fix
				return new RayHit(Axis.NEG_X, new Vector3(hitX, hitY, hitZ));
			}
		} else {
			hitDistance = dx2/pickRay.direction.x;
			hitX = x2;
			hitY = pickRay.origin.y - pickRay.direction.y*hitDistance;
			hitZ = pickRay.origin.z - pickRay.direction.z*hitDistance;
			if (hitY >= y1 && hitY <= y2 && hitZ >= z1 && hitZ <= z2) {
				return new RayHit(Axis.POS_X, new Vector3(hitX, hitY, hitZ));
			}
		}

		if (dy1 < dy2) {
			hitDistance = dy1/pickRay.direction.y;
			hitY = y1;
			hitX = pickRay.origin.x + pickRay.direction.x*hitDistance;
			hitZ = pickRay.origin.z + pickRay.direction.z*hitDistance;
			if (hitX >= x1 && hitX <= x2 && hitZ >= z1 && hitZ <= z2) {
				return new RayHit(Axis.NEG_Y, new Vector3(hitX, hitY, hitZ));
			}
		} else if (dy2 < dy1) {
			hitDistance = dy2/pickRay.direction.y;
			hitY = y2;
			hitX = pickRay.origin.x - pickRay.direction.x*hitDistance;
			hitZ = pickRay.origin.z - pickRay.direction.z*hitDistance;
			if (hitX >= x1 && hitX <= x2 && hitZ >= z1 && hitZ <= z2) {
				return new RayHit(Axis.POS_Y, new Vector3(hitX, hitY, hitZ));
			}
		}

		if (dz1 < dz2) {
			hitDistance = dz1/pickRay.direction.z;
			hitZ = z1;
			hitX = pickRay.origin.x + pickRay.direction.x*hitDistance;
			hitY = pickRay.origin.y + pickRay.direction.y*hitDistance;
			if (hitX >= x1 && hitX <= x2 && hitY >= y1 && hitY <= y2) {
				return new RayHit(Axis.NEG_Z, new Vector3(hitX, hitY, hitZ));
			}
		} else if (dz2 < dz1) {
			hitDistance = dz2/pickRay.direction.z;
			hitZ = z2;
			hitX = pickRay.origin.x - pickRay.direction.x*hitDistance;
			hitY = pickRay.origin.y - pickRay.direction.y*hitDistance;
			if (hitX >= x1 && hitX <= x2 && hitY >= y1 && hitY <= y2) {
				return new RayHit(Axis.POS_Z, new Vector3(hitX, hitY, hitZ));
			}
		}
		
		return null;
	}
}
