/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.is_great.pro.facetracking;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.vision.face.Face;
import com.is_great.pro.facetracking.ui.camera.GraphicOverlay;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = 50.0f;
    private static final float ID_X_OFFSET = -50.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

    private static final double SMILING_PROB_THRESHOLD = .15;
    private static final double EYE_OPEN_PROB_THRESHOLD = .5;

    private static final int COLOR_CHOICES[] = {
        Color.BLUE,
        Color.CYAN,
        Color.GREEN,
        Color.MAGENTA,
        Color.RED,
        Color.WHITE,
        Color.YELLOW
    };
    private static int mCurrentColorIndex = 0;

    private Paint mFacePositionPaint;
    private Paint mIdPaint;
    private Paint mBoxPaint;

    private volatile Face mFace;
    private int mFaceId;
    private Context mContext;

    FaceGraphic(GraphicOverlay overlay, Context context) {
        super(overlay);

        mContext=context;
        mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
        final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

    }

    void setId(int id) {
        mFaceId = id;
    }


    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }


    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;
        if (face == null) {
            return;
        }

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);

        canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);
        canvas.drawText("id: " + mFaceId, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);
        canvas.drawText("happiness: " + String.format("%.2f", face.getIsSmilingProbability()), x - ID_X_OFFSET, y - ID_Y_OFFSET, mIdPaint);

        String prediction = getPrediction(face.getEulerY(),face.getEulerZ());
        canvas.drawText("Prediction: "+prediction,x-ID_X_OFFSET,y-ID_Y_OFFSET+3*ID_TEXT_SIZE,mIdPaint);
        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);

        TextView textView = (TextView)((Activity)mContext).findViewById(R.id.faceUpdates);
        String data=textView.getText().toString();
        int len = data.length();
        String line =face.getId()+"  "+ getUpdates();
        if(len>60){
            String partial_data = data.substring(len-30,len);
            if(partial_data.contains(line)){
                //do nothing
            }else{
                textView.append("\nUserId:"+line);
            }
        }else{
            textView.append("\nUserId:"+line);
        }

        final ScrollView mScrollView=(ScrollView)((Activity)mContext).findViewById(R.id.scrollView);

        mScrollView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        }, 600);

    }

    private String getPrediction(float eulerY, float eulerZ) {
        String feature="";
        if(eulerZ<5f && eulerZ >=0f){
            if(eulerY>0f && eulerY<60f){
                feature="Facing straight right";
            }else{
                feature="no tilt";
            }
        }else if(eulerZ>5f && eulerZ<45f){
            if(eulerY>0f && eulerY<=60f){
                feature="facing slightly right up";
            }else {
                feature="Face Slightly tilted to right";
            }
        }else if(eulerZ>45f){
            if(eulerY>60f && eulerY!=0){
                feature="Facing right up";
            }else{
                feature="Face tilted to right";
            }
        }else if(eulerZ<0f && eulerZ >-5f){
            if(eulerY>-60f && eulerY!=0){
                feature="Facing right";
            }else{
                feature="no tilt";
            }
        }else if(eulerZ<-5f && eulerZ>-45f){
            if(eulerY>-60f && eulerY!=0){
                feature="Facing Left up";
            }else{
                feature="Face Slightly tilted to left";
            }
        }else{
            if(eulerY>-6f && eulerY!=0){
                feature="Facing Left up";
            }else{
                feature="Face tilted to left";
            }
        }

        return feature;
    }

    private String getUpdates(){
        String update;
        boolean smiling = mFace.getIsSmilingProbability() > SMILING_PROB_THRESHOLD;

        boolean leftEyeClosed = mFace.getIsLeftEyeOpenProbability() < EYE_OPEN_PROB_THRESHOLD;
        boolean rightEyeClosed = mFace.getIsRightEyeOpenProbability() < EYE_OPEN_PROB_THRESHOLD;
        if(smiling) {
            if (leftEyeClosed && !rightEyeClosed) {
                update="Left Wink";
            }  else if(rightEyeClosed && !leftEyeClosed){
                update = "Right WInk";
            } else if (leftEyeClosed){
                update = "Closed Eye Smile";
            } else {
                update = "Smile";
            }
        } else {
            if (leftEyeClosed && !rightEyeClosed) {
                update = "Left Wink Frawn";
            }  else if(rightEyeClosed && !leftEyeClosed){
                update = "Right Wink Frawn";
            } else if (leftEyeClosed){
                update = "Closed Eye Frawn";
            } else {
                update = "Frawn";
            }
        }

        return update;

    }
}
