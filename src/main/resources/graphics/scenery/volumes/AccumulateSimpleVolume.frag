if (vis)
{
    vec4 x = sampleVolume(wpos);
    float newAlpha = x.a;
    vec3 newColor = x.rgb;

//    if(newAlpha < minOpacity) {
//        transparentSample = true;
//    }
//
//    if(supersegmentIsOpen) {
//        float segLen = step - supSegStartPoint; //TODO get from texture
//        vec4 supersegmentAdjusted;
//        supersegmentAdjusted.rgb = curV.rgb / curV.a;
//        supersegmentAdjusted.a = adjustOpacity(curV.a, 1.0/segLen);
//
//        float diff = diffPremultiplied(supersegmentAdjusted, x);
//
//        bool newSupSeg = false;
//        if(diff > newSupSegThresh) {
//            newSupSeg = true;
//        }
//
//        if(lastSample || ((newSupSeg || transparentSample) && !lastSuperSegment)) {
//            superSegmentIsOpen = false;
//            supersegmentNum++;
//            supSegEndPoint = step; //TODO write to texture
//            //TODO write in texture VDI[supersegmentNum] = supersegmentAdjusted
//        }
//    }
//
//    if( (!lastSegment) && (!supersegmentIsOpen) && (!transparentSample) ) {
//        supersegmentIsOpen = true;
//        supSegStartPoint = step;
//        curV = vec4( 0 );
//    }
//
//    float w = adjustOpacity(newAlpha, nw + step * fwnw);
//    curV.rgb = curV.rgb + (1 - curV.a) * newColor * w;
//    curV.a = curV.a + (1 - curV.a) * w;

    v.rgb = v.rgb + (1.0f - v.a) * newColor * newAlpha;
    v.a = v.a + (1.0f - v.a) * newAlpha;

    if(v.a >= 1.0f) {
        break;
    }
}
