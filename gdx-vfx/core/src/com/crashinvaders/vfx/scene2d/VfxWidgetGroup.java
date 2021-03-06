/*******************************************************************************
 * Copyright 2019 metaphore
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.crashinvaders.vfx.scene2d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.crashinvaders.vfx.VfxManager;
import com.crashinvaders.vfx.framebuffer.VfxPingPongWrapper;
import com.crashinvaders.vfx.framebuffer.VfxFrameBuffer;

public class VfxWidgetGroup extends WidgetGroup {

    private final VfxManager vfxManager;
    private final CustomRendererAdapter rendererAdapter;
    private boolean initialized = false;
    private boolean resizePending = false;
    private boolean matchWidgetSize = false;

    public VfxWidgetGroup(Pixmap.Format pixelFormat) {
        vfxManager = new VfxManager(pixelFormat);
        rendererAdapter = new CustomRendererAdapter();
        super.setTransform(false);
    }

    public VfxManager getVfxManager() {
        return vfxManager;
    }

    public boolean isMatchWidgetSize() {
        return matchWidgetSize;
    }

    /**
     * @param matchWidgetSize if true, the internal {@link VfxManager} will be resized
     *                        to match {@link VfxWidgetGroup}'s size (stage units and not screen pixels).
     */
    public void setMatchWidgetSize(boolean matchWidgetSize) {
        if (this.matchWidgetSize == matchWidgetSize) return;

        this.matchWidgetSize = matchWidgetSize;
        resizePending = true;
    }

    @Override
    protected void setStage(Stage stage) {
        super.setStage(stage);

        if (stage != null) {
            initialize();
        } else {
            reset();
        }
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        resizePending = true;
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        // Update effects chain.
        vfxManager.update(delta);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        final VfxPingPongWrapper buffers = vfxManager.getPingPongWrapper();

        batch.end();

        performPendingResize();

        vfxManager.cleanUpBuffers();

        buffers.getSrcBuffer().addRenderer(rendererAdapter);
        buffers.getDstBuffer().addRenderer(rendererAdapter);
        vfxManager.beginCapture();

        batch.begin();

        validate();
        drawChildren(batch, parentAlpha);

        batch.end();

        vfxManager.endCapture();
        buffers.getSrcBuffer().removeRenderer(rendererAdapter);
        buffers.getDstBuffer().removeRenderer(rendererAdapter);

        vfxManager.applyEffects();

        batch.begin();

        // If something was captured, render result to the screen.
        if (vfxManager.hasResult()) {
            Color color = getColor();
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
            batch.draw(vfxManager.getResultBuffer().getFbo().getColorBufferTexture(),
                    getX(), getY(), getWidth(), getHeight(),
                    0f, 0f, 1f, 1f);
        }
    }

    @Override
    protected void drawChildren(Batch batch, float parentAlpha) {
        boolean capturing = vfxManager.isCapturing();

        if (capturing) {
            // Imitate "transform" child drawing for when capturing into VfxManager.
            super.setTransform(true);
        }
        if (!capturing) {
            // Clip children to VfxWidget area when not capturing into FBO.
            clipBegin();
        }

        super.drawChildren(batch, parentAlpha);
        batch.flush();

        if (capturing) {
            super.setTransform(false);
        }

        if (!capturing) {
            clipEnd();
        }
    }

    @Deprecated
    @Override
    public void setCullingArea(Rectangle cullingArea) {
        throw new UnsupportedOperationException("VfxWidgetGroup doesn't support culling area.");
    }

    @Deprecated
    @Override
    public void setTransform(boolean transform) {
        throw new UnsupportedOperationException("VfxWidgetGroup doesn't support transform.");
    }

    private void initialize() {
        if (initialized) return;

        performPendingResize();

        rendererAdapter.initialize(getStage().getBatch());

        resizePending = false;
        initialized = true;
    }

    private void reset() {
        if (!initialized) return;

        vfxManager.dispose();

        rendererAdapter.reset();

        resizePending = false;
        initialized = false;
    }

    private void performPendingResize() {
        if (!resizePending) return;

        final int width;
        final int height;

        // Size may be zero if the widget wasn't laid out yet.
        if ((int)getWidth() == 0 || (int)getHeight() == 0) {
            // If the size of the widget is not defined,
            // just resize to a small buffer to keep the memory footprint low.
            width = 16;
            height = 16;

        } else if (matchWidgetSize) {
            // Set buffer to match the size of the widget.
            width = MathUtils.floor(getWidth());
            height = MathUtils.floor(getHeight());

        } else {
            // Set buffer to match the screen pixel density.
            Viewport viewport = getStage().getViewport();
            float ppu = viewport.getScreenWidth() / viewport.getWorldWidth();
            width = MathUtils.floor(getWidth() * ppu);
            height = MathUtils.floor(getHeight() * ppu);

            rendererAdapter.updateOwnProjection();
        }

        vfxManager.resize(width, height);

        resizePending = false;
    }

    private class CustomRendererAdapter implements VfxFrameBuffer.Renderer {
        private final Matrix4 preservedProjection = new Matrix4();
        private final Matrix4 ownProjection = new Matrix4();

        private Batch batch;

        public void initialize(Batch batch) {
            this.batch = batch;
        }

        private void reset() {
            batch = null;
        }

        @Override
        public void flush() {
            batch.flush();
        }

        @Override
        public void assignLocalMatrices(Matrix4 projection, Matrix4 transform) {
            preservedProjection.set(batch.getProjectionMatrix());

            if (!matchWidgetSize) {
                projection = ownProjection;
            }

            batch.setProjectionMatrix(projection);
        }

        @Override
        public void restoreOwnMatrices() {
            batch.setProjectionMatrix(preservedProjection);
        }

        public void updateOwnProjection() {
            ownProjection.setToOrtho2D(0f, 0f, getWidth(), getHeight());
        }
    }
}
