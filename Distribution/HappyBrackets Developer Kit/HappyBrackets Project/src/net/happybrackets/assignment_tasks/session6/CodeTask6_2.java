package net.happybrackets.assignment_tasks.session6;

import net.happybrackets.extras.assignment_autograding.SimpleCheckable;

import java.util.Hashtable;

/**
 * In this task, you are being passed a Hashtable with three elements, a MIDI note, a base-freq and a set of octave frequencies. The data in the Hashtable may be wrong. The MIDI value is always correct. The base-freq should correspond to the MIDI value, and the octaves value should contain for floats representing the frequencies of the four octaves above the base-freq.
 *
 * You will be passed one Hashtable, as in the example below. You should do two things:
 * 1) check the data in the Hashtable and report which information is wrong. e.g., if the base-freq is wrong then output a line saying base-freq. If the first element in the octaves array is wrong then output a line saying octaves0, then octaves1 for the second element and so on.
 * 2) correct the data in the Hashtable. After your task is completed, the Hashtable will be checked for correctness, including making sure there are no extra elements.
 *
 */
public class CodeTask6_2 implements SimpleCheckable {

    public static void main(String[] args) {
        StringBuffer buf = new StringBuffer();
        Hashtable<String, Object> testShare = new Hashtable<>();
        testShare.put("midi", 60);
        testShare.put("base-freq", 180.0f);
        testShare.put("octaves", new float[] {320.0f, 640.0f, 1280.0f, 2560.0f} );
        new CodeTask6_2().task(new Object[]{buf, testShare});
        System.out.println(buf);
    }

    @Override
    public void task(Object... objects) {
        //********** do your work here ONLY **********
        //your objects...
        StringBuffer buf = (StringBuffer)objects[0];
        Hashtable<String, Object> share = (Hashtable<String, Object>)objects[1];
        //do stuff here, remove the following line
        buf.append("Hello World!\n");
        //********** do your work here ONLY **********
    }
}
