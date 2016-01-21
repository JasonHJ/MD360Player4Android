package com.asha.md360player4android.lesson4;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.asha.md360player4android.R;
import com.asha.md360player4android.Sphere;
import com.asha.md360player4android.common.RawResourceReader;
import com.asha.md360player4android.common.ShaderHelper;
import com.asha.md360player4android.common.TextureHelper;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * This class implements our custom renderer. Note that the GL10 parameter passed in is unused for OpenGL ES 2.0
 * renderers -- the static class GLES20 is used instead.
 */
public class LessonFourRenderer implements GLSurfaceView.Renderer 
{	
	/** Used for debug logs. */
	private static final String TAG = "LessonFourRenderer";
	
	private final Context mActivityContext;
	
	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];

	private float[] mMVMatrix = new float[16];
	
	/** 
	 * Stores a copy of the model matrix specifically for the light position.
	 */
	private float[] mLightModelMatrix = new float[16];	
	
	/** This will be used to pass in the transformation matrix. */
	private int mMVPMatrixHandle;
	
	/** This will be used to pass in the modelview matrix. */
	private int mMVMatrixHandle;
	
	/** This will be used to pass in the texture. */
	private int mTextureUniformHandle;
	
	/** This will be used to pass in model position information. */
	private int mPositionHandle;
	
	/** This will be used to pass in model texture coordinate information. */
	private int mTextureCoordinateHandle;

	/** Size of the position data in elements. */
	private final int mPositionDataSize = 3;	
	
	/** Size of the color data in elements. */
	private final int mColorDataSize = 4;	
	
	/** Size of the normal data in elements. */
	private final int mNormalDataSize = 3;
	
	/** Size of the texture coordinate data in elements. */
	private final int mTextureCoordinateDataSize = 2;
	
	/** Used to hold a light centered on the origin in model space. We need a 4th coordinate so we can get translations to work when
	 *  we multiply this by our transformation matrices. */
	private final float[] mLightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
	
	/** Used to hold the current position of the light in world space (after transformation via model matrix). */
	private final float[] mLightPosInWorldSpace = new float[4];
	
	/** Used to hold the transformed position of the light in eye space (after transformation via modelview matrix) */
	private final float[] mLightPosInEyeSpace = new float[4];
	
	/** This is a handle to our cube shading program. */
	private int mProgramHandle;
		
	/** This is a handle to our light point program. */
	private int mPointProgramHandle;
	
	/** This is a handle to our texture data. */
	private int mTextureDataHandle;
	
	/**
	 * Initialize the model data.
	 */
	public LessonFourRenderer(final Context activityContext)
	{	
		mActivityContext = activityContext;
		sphere = new Sphere(mActivityContext);
	}
	
	protected String getVertexShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_vertex_shader);
	}
	
	protected String getFragmentShader()
	{
		return RawResourceReader.readTextFileFromRawResource(mActivityContext, R.raw.per_pixel_fragment_shader);
	}
	
	@Override
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config) 
	{
		// Set the background clear color to black.
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		
		// Use culling to remove back faces.
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		
		// Enable depth testing
		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		
		init();
		initSphere2();
	}


	private void init(){
		final String vertexShader = getVertexShader();
		final String fragmentShader = getFragmentShader();

		final int vertexShaderHandle = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
		final int fragmentShaderHandle = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

		mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
				new String[] {"a_Position", "a_TexCoordinate"});

		// Load the texture
		mTextureDataHandle = TextureHelper.loadTexture(mActivityContext, R.drawable.demo);


		// Set program handles for cube drawing.
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
		mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
		mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
		mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
		mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");
	}

	private void initSphere(){
		IntBuffer vertexBufferId = IntBuffer.allocate(1);
		IntBuffer textureBufferId = IntBuffer.allocate(1);
		IntBuffer vertexIndicesBufferId = IntBuffer.allocate(1);
		FloatBuffer vertexBuffer = sphere.getVerticesBuffer();
		FloatBuffer textureBuffer = sphere.getTexCoordsBuffer();
		IntBuffer indicesBuffer = sphere.getIndicesBuffer();
		int numVertices = sphere.numVertices;
		int numIndices = sphere.numIndices;

		GLES20.glGenBuffers(1,vertexBufferId);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId.get(0));
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, numVertices*3*4, vertexBuffer, GLES20.GL_STATIC_DRAW);
		GLES20.glVertexAttribPointer(mPositionHandle,mPositionDataSize,GLES20.GL_FLOAT, false, 0, 0);
		GLES20.glEnableVertexAttribArray(mPositionHandle);

		GLES20.glGenBuffers(1,textureBufferId);
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId.get(0));
		GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, numVertices*2*4, textureBuffer, GLES20.GL_DYNAMIC_DRAW);
		GLES20.glVertexAttribPointer(mTextureCoordinateHandle,mTextureCoordinateDataSize,GLES20.GL_FLOAT, false, 0, 0);
		GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

		GLES20.glGenBuffers(1,vertexIndicesBufferId);
		GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,vertexIndicesBufferId.get(0));
		GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,numIndices*4,indicesBuffer,GLES20.GL_STATIC_DRAW);
	}

	private void initSphere2(){

		FloatBuffer vertexBuffer = sphere.getVerticesBuffer();
		FloatBuffer textureBuffer = sphere.getTexCoordsBuffer();
		vertexBuffer.position(0);
		textureBuffer.position(0);

		GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false, 0, vertexBuffer);
		GLES20.glEnableVertexAttribArray(mPositionHandle);

		GLES20.glVertexAttribPointer(mTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
		GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
	}


	private void update1(int width, int height){
		// Create a new perspective projection matrix. The height will stay the same
		// while the width will vary as per aspect ratio.

		// The below glEnable() call is a holdover from OpenGL ES 1, and is not needed in OpenGL ES 2.
		// Enable texture mapping
		// GLES20.glEnable(GLES20.GL_TEXTURE_2D);

		// Position the eye in front of the origin.
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = 20.0f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = 0.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;

		Matrix.setIdentityM(mViewMatrix, 0);
		// Set the view matrix. This matrix can be said to represent the camera position.
		// NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
		// view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);


		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);





		//final float ratio = (float) width / height;
		final float left = -0.5f;
		final float right = 0.5f;
		final float bottom = -0.5f;
		final float top = 0.5f;
		final float near = 1;
		final float far = 500;
		Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);

		// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
		// (which currently contains model * view).
		Matrix.multiplyMM(mMVMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

		// This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
		// (which now contains model * view * projection).
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVMatrix, 0);
	}

	@Override
	public void onSurfaceChanged(GL10 glUnused, int width, int height) 
	{
		// Set the OpenGL viewport to the same size as the surface.
		GLES20.glViewport(0, 0, width, height);

		update1(width,height);


	}

	@Override
	public void onDrawFrame(GL10 glUnused) 
	{
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);			        
                

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mProgramHandle);
        

        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        
        // Bind the texture to this unit.
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureDataHandle);
        
        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);
        
		drawCube();

        // Draw a point to indicate the light.
        // GLES20.glUseProgram(mPointProgramHandle);
        // drawLight();
	}				
	
	/**
	 * Draws a cube.
	 */

	Sphere sphere;
	private void drawCube()
	{

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        
        // Draw the cube.
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sphere.numIndices);
		//GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphere.numIndices,GLES20.GL_UNSIGNED_SHORT,0);
	}	

}