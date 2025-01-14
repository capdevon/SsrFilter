/*
 * Copyright (c) 2009-2019 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.jme3.post.filters;

import com.jme3.asset.AssetManager;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.Image;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Based on Riccardo Balbo's SSR shader for jMonkeyEngine: https://github.com/riccardobl/SimpleSSRShader
 * @author rickard
 */
public class SsrFilter extends Filter{
    
    private Vector2f frustumNearFar;

    private RenderManager renderManager;
    private ViewPort viewPort;
    private Pass normalPass;
    private Pass ssrPass;
    private Pass[] blurPass;
    private boolean approximateNormals = false;
    private boolean approximateGlossiness = true;

    private float downSampleFactor = 1f;

    private Material ssrMaterial;
    private boolean fastBlur = false;
    private int raySteps = 16;
    private boolean sampleNearby = true;
    private float stepLength = 1.0f;
    private float blurScale = 1f;
    private float sigma = 5f;
    private float reflectionFactor = 1f;
    private Vector2f nearFade = new Vector2f(0.01f, 1.0f);
    private Vector2f farFade = new Vector2f(200f, 300f);
    private int blurPasses = 4;
    private Image.Format ssrImageFormat = Image.Format.RGBA16F;
    private boolean rgNormalMap;
    private boolean glossinessPackedInNormalB = true;

    public SsrFilter(){
        super("SSR Filter");
    }
    
    @Override
    protected boolean isRequiresDepthTexture() {
        return true;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        
        this.renderManager = renderManager;
        this.viewPort = vp;
        int screenWidth = w;
        int screenHeight = h;
                postRenderPasses = new ArrayList<>();


        frustumNearFar = new Vector2f();

        float farY = (vp.getCamera().getFrustumTop() / vp.getCamera().getFrustumNear()) * vp.getCamera().getFrustumFar();
        float farX = farY * ((float) screenWidth / (float) screenHeight);
        frustumNearFar.x = vp.getCamera().getFrustumNear();
        frustumNearFar.y = vp.getCamera().getFrustumFar();
        
        
//        if(!approximateNormals){
            normalPass = new Pass();
            normalPass.init(renderManager.getRenderer(), (int) (screenWidth / downSampleFactor), (int) (screenHeight / downSampleFactor), Image.Format.RGBA8, Image.Format.Depth);
//        }

        ssrMaterial = new Material(manager, "MatDefs/SSR/ssr.j3md"); 
//        if(!approximateNormals){
            ssrMaterial.setTexture("Normals", normalPass.getRenderedTexture());
//        }
        ssrMaterial.setInt("RaySamples", raySteps);
        ssrMaterial.setInt("NearbySamples", sampleNearby ? 4 : 0);
        ssrMaterial.setFloat("StepLength", stepLength);
        ssrMaterial.setFloat("ReflectionFactor", reflectionFactor);
        ssrMaterial.setBoolean("GlossinessPackedInNormalB", glossinessPackedInNormalB);
        ssrMaterial.setBoolean("RGNormalMap", glossinessPackedInNormalB);
        ssrMaterial.setBoolean("ApproximateNormals", approximateNormals);
        ssrMaterial.setVector2("NearReflectionsFade", nearFade);
        ssrMaterial.setVector2("FarReflectionsFade", farFade);
        
        ssrPass = new Pass("SSR pass") {

            @Override
            public boolean requiresDepthAsTexture() {
                return true;
            }

            @Override
            public boolean requiresSceneAsTexture() {
                return true; //To change body of generated methods, choose Tools | Templates.
            }
            
            
        };

        ssrPass.init(renderManager.getRenderer(), (int) (screenWidth / downSampleFactor), (int) (screenHeight / downSampleFactor), ssrImageFormat, Image.Format.Depth, 1, ssrMaterial);
        postRenderPasses.add(ssrPass);
        
        Material blurMaterial = new Material(manager, "MatDefs/SSR/ssrBlur.j3md"); 
        blurMaterial.setTexture("SSR", ssrPass.getRenderedTexture());
        blurMaterial.setBoolean("Horizontal", true);
        blurMaterial.setBoolean("FastBlur", fastBlur);
        blurMaterial.setFloat("BlurScale", blurScale);
        blurMaterial.setFloat("Sigma", sigma);
        
        blurPass = new Pass[blurPasses-1];
        for(int i = 0; i < blurPasses-1; i++){
            blurPass[i] = new Pass("Blur Pass"); 
            Material passMat = blurMaterial.clone();
            passMat.setBoolean("Horizontal", i % 2 == 0);
            if(i == 0){
                passMat.setTexture("SSR", ssrPass.getRenderedTexture());
            } else {
                passMat.setTexture("SSR", blurPass[i-1].getRenderedTexture());
            }
            blurPass[i].init(renderManager.getRenderer(), (int) (screenWidth / downSampleFactor), (int) (screenHeight / downSampleFactor), Image.Format.RGBA8, Image.Format.Depth, 1, passMat);
            postRenderPasses.add(blurPass[i]);
        }

        material = new Material(manager, "MatDefs/SSR/ssrBlur.j3md"); 
        material.setTexture("SSR", blurPasses > 1 ? blurPass[blurPasses-2].getRenderedTexture() : ssrPass.getRenderedTexture());
        material.setBoolean("Horizontal", blurPasses % 2 == 1);
        material.setBoolean("FastBlur", fastBlur);
        material.setFloat("BlurScale", blurScale);
        material.setFloat("Sigma", sigma);
        
}
    
    @Override
    protected void postQueue(RenderQueue queue) {
        if(!approximateNormals) {
            Renderer r = renderManager.getRenderer();
            r.setFrameBuffer(normalPass.getRenderFrameBuffer());
            renderManager.getRenderer().clearBuffers(true, true, true);
            if(glossinessPackedInNormalB){
                renderManager.setForcedTechnique("PreNormalGlossPass");
            } else {
                renderManager.setForcedTechnique("PreNormalPass");
            }
            
            renderManager.renderViewPortQueues(viewPort, false);
            renderManager.setForcedTechnique(null);
            renderManager.getRenderer().setFrameBuffer(viewPort.getOutputFrameBuffer());
        }
    }

    @Override
    protected void cleanUpFilter(Renderer r) {
        if(normalPass != null){
            normalPass.cleanup(r);
        }
    }    

    @Override
    protected Material getMaterial() {
        return material;
    }
    
    /**
     * If true, any passed normals won't be used. Instead they will be approximated using the depth map
     * @param approximateNormals 
     */
    public void setApproximateNormals(boolean approximateNormals) {
        this.approximateNormals = approximateNormals;
        if(ssrMaterial != null){
            ssrMaterial.setBoolean("ApproximateNormals", approximateNormals);
        }
    }

    /**
     * Value to scale (down) the textures the filter uses.
     * Some values work better than others with approximateNormals. Good values: 1.5f, 3f;
     * @param downSampleFactor 
     */
    public void setDownSampleFactor(float downSampleFactor) {
        this.downSampleFactor = downSampleFactor;
    }
    
    /**
     * Using a faster blur function in the blur pass. Default false
     * @param fastBlur 
     */
    public void setFastBlur(boolean fastBlur) {
        this.fastBlur = fastBlur;
        if(material != null){
            material.setBoolean("FastBlur", fastBlur);
        }
    }

    /**
     * Amount of steps
     * @param raySteps 
     */
    public void setRaySteps(int raySteps) {
        this.raySteps = raySteps;
        if(ssrMaterial != null){
            ssrMaterial.setInt("RaySamples", raySteps);
        }
    }

    /**
     * Whether to sample nearby ray hits for better accuracy
     * @param sampleNearby 
     */
    public void setSampleNearby(boolean sampleNearby) {
        this.sampleNearby = sampleNearby;
        if(ssrMaterial != null){
            ssrMaterial.setInt("NearbySamples", sampleNearby ? 4 : 0);
        }
    }

    /**
     * Length of each ray step
     * @param stepLength 
     */
    public void setStepLength(float stepLength) {
        this.stepLength = stepLength;
        if(ssrMaterial != null){
            ssrMaterial.setFloat("StepLength", stepLength);
        }
    }

    /**
     * Scale for blur. Only used if fastBlur is true
     * @param blurScale 
     */
    public void setBlurScale(float blurScale) {
        this.blurScale = blurScale;
        if(material != null){
            material.setFloat("BlurScale", blurScale);
        }
    }

    /**
     * Sigma for regular gaussian blur. Only used if fastBlur is false
     * @param sigma 
     */
    public void setSigma(float sigma) {
        this.sigma = sigma;
        if(material != null){
            material.setFloat("Sigma", sigma);
        }
    }

    public float getReflectionFactor() {
        return reflectionFactor;
    }

    /**
     * Sets the overall reflection amount. Scales between 0 and 1
     * @param reflectionFactor 
     */
    public void setReflectionFactor(float reflectionFactor) {
        this.reflectionFactor = reflectionFactor;
        if(ssrMaterial != null){
            ssrMaterial.setFloat("ReflectionFactor", reflectionFactor);
        }
    }
    
       
    
    public boolean isApproximateGlossiness() {
        return approximateGlossiness;
    }

    public void setApproximateGlossiness(boolean approximateGlossiness) {
        this.approximateGlossiness = approximateGlossiness;
        if(ssrMaterial != null){
            ssrMaterial.setBoolean("ApproximateGlossiness", approximateGlossiness);
        }
    }

    public Vector2f getNearFade() {
        return nearFade;
    }

    public void setNearFade(Vector2f nearFade) {
        this.nearFade = nearFade;
        if(ssrMaterial != null){
            ssrMaterial.setVector2("NearReflectionsFade", nearFade);
        }
    }

    public Vector2f getFarFade() {
        return farFade;
    }

    public void setFarFade(Vector2f farFade) {
        this.farFade = farFade;
        if(ssrMaterial != null){
            ssrMaterial.setVector2("FarReflectionsFade", farFade);
        }
    }

    public int getBlurPasses() {
        return blurPasses;
    }

    public void setBlurPasses(int blurPasses) {
        this.blurPasses = blurPasses;
    }

    public Image.Format getSsrImageFormat() {
        return ssrImageFormat;
    }

    public void setSsrImageFormat(Image.Format ssrImageFormat) {
        this.ssrImageFormat = ssrImageFormat;
    }

    public boolean isGlossinessPackedInNormalB() {
        return glossinessPackedInNormalB;
    }

    public void setGlossinessPackedInNormalB(boolean glossinessPackedInNormalB) {
        this.glossinessPackedInNormalB = glossinessPackedInNormalB;
        if(ssrMaterial != null){
            ssrMaterial.setBoolean("GlossinessPackedInNormalB", glossinessPackedInNormalB);
            ssrMaterial.setBoolean("RGNormalMap", glossinessPackedInNormalB);
        }
    }

    @Override
    public SsrFilter clone(){
        SsrFilter clone = new SsrFilter();
        clone.approximateNormals = approximateNormals;
        clone.approximateGlossiness = approximateGlossiness;
        clone.sampleNearby = sampleNearby;
        clone.fastBlur = fastBlur;
        clone.downSampleFactor = downSampleFactor;
        clone.stepLength = stepLength;
        clone.blurScale = blurScale;
        clone.sigma = sigma;
        clone.reflectionFactor = reflectionFactor;
        clone.raySteps = raySteps;
        clone.blurPasses = blurPasses;
        clone.glossinessPackedInNormalB = glossinessPackedInNormalB;
        clone.ssrImageFormat = ssrImageFormat;
        clone.nearFade = nearFade.clone();
        clone.farFade = farFade.clone();
        return clone;
    }
    
    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule oc = ex.getCapsule(this);
        oc.write(approximateNormals, "approximateNormals", true);
        oc.write(approximateGlossiness, "approximateGlossiness", true);
        oc.write(sampleNearby, "sampleNearby", true);
        oc.write(fastBlur, "fastBlur", true);
        oc.write(downSampleFactor, "downSampleFactor", 1f);
        oc.write(stepLength, "stepLength", 1f);
        oc.write(blurScale, "blurScale", 1f);
        oc.write(sigma, "sigma", 5f);
        oc.write(reflectionFactor, "reflectionFactor", 1f);
        oc.write(raySteps, "raySteps", 16);
        oc.write(blurPasses, "blurPasses", 2);
        oc.write(glossinessPackedInNormalB, "glossinessPackedInNormalB", false);
        oc.write(ssrImageFormat.toString(), "ssrImageFormat", Image.Format.RGBA16F.toString());
        oc.write(new float[]{nearFade.x, nearFade.y}, "nearFade", new float[]{0.01f, 1.0f});
        oc.write(new float[]{farFade.x, farFade.y}, "farFade", new float[]{200f, 300f});
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule ic = im.getCapsule(this);
        approximateNormals = ic.readBoolean("approximateNormals", true);
        approximateGlossiness = ic.readBoolean("approximateGlossiness", true);
        sampleNearby = ic.readBoolean("sampleNearby", true);
        fastBlur = ic.readBoolean("fastBlur", true);
        downSampleFactor = ic.readFloat("downSampleFactor", 1f);
        stepLength = ic.readFloat("stepLength", 1f);
        blurScale = ic.readFloat("blurScale", 1f);
        sigma = ic.readFloat("sigma", 5f);
        reflectionFactor = ic.readFloat("reflectionFactor", 1f);
        raySteps = ic.readInt("raySteps", 16);
        blurPasses = ic.readInt("blurPasses", 2);
        glossinessPackedInNormalB = ic.readBoolean("glossinessPackedInNormalB", false);
        String format = ic.readString("ssrImageFormat", Image.Format.RGBA16F.toString());
        ssrImageFormat = Image.Format.valueOf(format);
        float[] nearArray = ic.readFloatArray("nearFade", new float[]{0.01f, 1.0f});
        nearFade = new Vector2f(nearArray[0], nearArray[1]);
        float[] farArray = ic.readFloatArray("farFade", new float[]{200f, 300f});
        farFade = new Vector2f(farArray[0], farArray[1]);
    }
    
}
