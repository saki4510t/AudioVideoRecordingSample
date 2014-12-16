package com.serenegiant.glutils;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014 saki t_saki@serenegiant.com
 *
 * File name: RenderHandler.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

/**
 * Helper class to draw texture to whole view on private thread
 */
public final class RenderHandler implements Runnable {
	private static final boolean DEBUG = false;	// TODO set false on release
	private static final String TAG = "RenderHandler";

	private final Object mSync = new Object();
    private EGLContext mShard_context;
    private Surface mSurface;
	private int mTexId = -1;
	private float[] mTexMatrix;
	
	private boolean mRequestSetEglContext; 
	private boolean mRequestDraw;
	private boolean mRequestRelease;

	public static final RenderHandler createHandler(String name) {
		if (DEBUG) Log.v(TAG, "createHandler:");
		final RenderHandler handler = new RenderHandler();
		synchronized (handler.mSync) {
			new Thread(handler, name != null ? name : TAG).start();
			try {
				handler.mSync.wait();
			} catch (InterruptedException e) {
			}
		}
		return handler;
	}

	public final void setEglContext(EGLContext shared_context, int tex_id, Surface surface) {
		if (DEBUG) Log.i(TAG, "setEglContext:");
		synchronized (mSync) {
			if (mRequestRelease) return;
			mShard_context = shared_context;
			mTexId = tex_id;
			mSurface = surface;
			mRequestSetEglContext = true;
			mSync.notifyAll();
			try {
				mSync.wait();
			} catch (InterruptedException e) {
			}
		}
	}

	public final void draw() {
		draw(mTexId, mTexMatrix);
	}

	public final void draw(int tex_id) {
		draw(tex_id, mTexMatrix);
	}

	public final void draw(final float[] tex_matrix) {
		draw(mTexId, tex_matrix);
	}
	
	public final void draw(int tex_id, final float[] tex_matrix) {
		synchronized (mSync) {
			if (mRequestRelease) return;
			mTexId = tex_id;
			mTexMatrix = tex_matrix;
			mRequestDraw = true;
			mSync.notifyAll();
			try {
				mSync.wait();
			} catch (InterruptedException e) {
			}
		}
	}

	public final void release() {
		if (DEBUG) Log.i(TAG, "release:");
		synchronized (mSync) {
			if (mRequestRelease) return;
			mRequestRelease = true;
			mSync.notifyAll();
			try {
				mSync.wait();
			} catch (InterruptedException e) {
			}
		}
	}

//********************************************************************************
//********************************************************************************
	private EGLBase mEgl;
	private EGLBase.EglSurface mInputSurface;
	private GLDrawer2D mDrawer;

	@Override
	public final void run() {
		if (DEBUG) Log.i(TAG, "RenderHandler thread started:");
		synchronized (mSync) {
			mRequestSetEglContext = mRequestDraw = mRequestRelease = false;
			mSync.notifyAll();
		}
        boolean isRunning = true;
        boolean localRequestDraw;
        while (isRunning) {
        	synchronized (mSync) {
        		if (mRequestRelease) break;
	        	if (mRequestSetEglContext) {
	        		mRequestSetEglContext = false;
	        		internalPrepare();
	        	}
	        	localRequestDraw = mRequestDraw;
	        	mRequestDraw = false;
        	}
        	if (localRequestDraw) {
        		if ((mEgl != null) && mTexId >= 0) {
            		mInputSurface.makeCurrent();
            		mDrawer.draw(mTexId, mTexMatrix);
            		mInputSurface.swap();
        		}
        		synchronized (mSync) {
        			mSync.notifyAll();
        		}
        	} else {
        		synchronized(mSync) {
        			try {
						mSync.wait();
					} catch (InterruptedException e) {
						break;
					}
        		}
        	}
        }
        synchronized (mSync) {
        	isRunning = false;
        	mRequestRelease = true;
            internalRelease();
            mSync.notifyAll();
        }
		if (DEBUG) Log.i(TAG, "RenderHandler thread finished:");
	}

	private final void internalPrepare() {
		if (DEBUG) Log.i(TAG, "internalPrepare:");
		internalRelease();
		mEgl = new EGLBase(mShard_context, false);
		mInputSurface = mEgl.createFromSurface(mSurface);
		mInputSurface.makeCurrent();
		mDrawer = new GLDrawer2D();
		mSurface = null;
		mSync.notifyAll();
	}

	private final void internalRelease() {
		if (DEBUG) Log.i(TAG, "internalRelease:");
		if (mInputSurface != null) {
			mInputSurface.release();
			mInputSurface = null;
		}
		if (mDrawer != null) {
			mDrawer.release();
			mDrawer = null;
		}
		if (mEgl != null) {
			mEgl.release();
			mEgl = null;
		}
	}

}
