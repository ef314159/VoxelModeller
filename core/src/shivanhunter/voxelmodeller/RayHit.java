package shivanhunter.voxelmodeller;

import shivanhunter.voxelmodeller.VoxelModel.Axis;

import com.badlogic.gdx.math.Vector3;

public class RayHit {
	public static final int AXIS_X = 1, AXIS_Y = 2, AXIS_Z = 3;
	
	public final Axis axis;
	public final Vector3 hitPoint;
	
	public RayHit(Axis axis, Vector3 hitPoint) {
		this.hitPoint = hitPoint;
		this.axis = axis;
	}
}
