/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.warp.WarpPreferencias;

import org.json.JSONException;
import org.json.JSONObject;

public class VirtualControllerConfigurationLoader {
    public static final String OSC_PREFERENCE = "OSC";

    private static int getPercent(
            int percent,
            int total) {
        return (int) (((float) total / (float) 100) * (float) percent);
    }

    private static DigitalPad createDigitalPad(
            final VirtualController controller,
            final Context context) {

        DigitalPad digitalPad = new DigitalPad(controller, context);
        digitalPad.addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();

                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    inputContext.inputMap |= ControllerPacket.LEFT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.LEFT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    inputContext.inputMap |= ControllerPacket.RIGHT_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    inputContext.inputMap |= ControllerPacket.UP_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.UP_FLAG;
                }
                if ((direction & DigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    inputContext.inputMap |= ControllerPacket.DOWN_FLAG;
                }
                else {
                    inputContext.inputMap &= ~ControllerPacket.DOWN_FLAG;
                }

                controller.sendControllerInputContext();
            }
        });

        return digitalPad;
    }

    private static DigitalButton createDigitalButton(
            final int elementId,
            final int keyShort,
            final int keyLong,
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        DigitalButton button = new DigitalButton(controller, elementId, layer, context);
        button.setText(text);
        button.setIcon(icon);

        button.addDigitalButtonListener(new DigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyShort;

                controller.sendControllerInputContext();
            }

            @Override
            public void onLongClick() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap |= keyLong;

                controller.sendControllerInputContext();
            }

            @Override
            public void onRelease() {
                VirtualController.ControllerInputContext inputContext =
                        controller.getControllerInputContext();
                inputContext.inputMap &= ~keyShort;
                inputContext.inputMap &= ~keyLong;

                controller.sendControllerInputContext();
            }
        });

        return button;
    }

    private static DigitalButton createLeftTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        LeftTrigger button = new LeftTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static DigitalButton createRightTrigger(
            final int layer,
            final String text,
            final int icon,
            final VirtualController controller,
            final Context context) {
        RightTrigger button = new RightTrigger(controller, layer, context);
        button.setText(text);
        button.setIcon(icon);
        return button;
    }

    private static AnalogStick createLeftStick(
            final VirtualController controller,
            final Context context) {
        return new LeftAnalogStick(controller, context);
    }

    private static AnalogStick createRightStick(
            final VirtualController controller,
            final Context context) {
        return new RightAnalogStick(controller, context);
    }


    private static final int TRIGGER_L_BASE_X = 1;
    private static final int TRIGGER_R_BASE_X = 92;
    private static final int TRIGGER_DISTANCE = 23;
    private static final int TRIGGER_BASE_Y = 31;
    private static final int TRIGGER_WIDTH = 12;
    private static final int TRIGGER_HEIGHT = 9;

    // Face buttons are defined based on the Y button (button number 9)
    private static final int BUTTON_BASE_X = 106;
    private static final int BUTTON_BASE_Y = 1;
    private static final int BUTTON_SIZE = 10;

    private static final int DPAD_BASE_X = 4;
    private static final int DPAD_BASE_Y = 41;
    private static final int DPAD_SIZE = 30;

    private static final int ANALOG_L_BASE_X = 6;
    private static final int ANALOG_L_BASE_Y = 4;
    private static final int ANALOG_R_BASE_X = 98;
    private static final int ANALOG_R_BASE_Y = 42;
    private static final int ANALOG_SIZE = 26;

    private static final int L3_R3_BASE_Y = 60;

    private static final int START_X = 83;
    private static final int BACK_X = 34;
    private static final int START_BACK_Y = 64;
    private static final int START_BACK_WIDTH = 12;
    private static final int START_BACK_HEIGHT = 7;

    // Make the Guide Menu be in the center of START and BACK menu
    private static final int GUIDE_X = 64 - START_BACK_WIDTH / 2;
    private static final int GUIDE_Y = START_BACK_Y;

    public static void createDefaultLayout(final VirtualController controller, final Context context) {

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        Grade g = new Grade(screen, WarpPreferencias.tamanhoOsc(context));

        // NOTE: Some of these getPercent() expressions seem like they can be combined
        // into a single call. Due to floating point rounding, this isn't actually possible.

        if (!config.onlyL3R3)
        {
            controller.addElement(createDigitalPad(controller, context),
                    g.esquerda(DPAD_BASE_X),
                    g.y(DPAD_BASE_Y),
                    g.t(DPAD_SIZE),
                    g.t(DPAD_SIZE)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_A,
                    !config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    !config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    g.direita(BUTTON_BASE_X),
                    g.y(BUTTON_BASE_Y + 2 * BUTTON_SIZE),
                    g.t(BUTTON_SIZE),
                    g.t(BUTTON_SIZE)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_B,
                    config.flipFaceButtons ? ControllerPacket.A_FLAG : ControllerPacket.B_FLAG, 0, 1,
                    config.flipFaceButtons ? "A" : "B", -1, controller, context),
                    g.direita(BUTTON_BASE_X + BUTTON_SIZE),
                    g.y(BUTTON_BASE_Y + BUTTON_SIZE),
                    g.t(BUTTON_SIZE),
                    g.t(BUTTON_SIZE)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_X,
                    !config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    !config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    g.direita(BUTTON_BASE_X - BUTTON_SIZE),
                    g.y(BUTTON_BASE_Y + BUTTON_SIZE),
                    g.t(BUTTON_SIZE),
                    g.t(BUTTON_SIZE)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_Y,
                    config.flipFaceButtons ? ControllerPacket.X_FLAG : ControllerPacket.Y_FLAG, 0, 1,
                    config.flipFaceButtons ? "X" : "Y", -1, controller, context),
                    g.direita(BUTTON_BASE_X),
                    g.y(BUTTON_BASE_Y),
                    g.t(BUTTON_SIZE),
                    g.t(BUTTON_SIZE)
            );

            controller.addElement(createLeftTrigger(
                    1, "LT", -1, controller, context),
                    g.esquerda(TRIGGER_L_BASE_X),
                    g.y(TRIGGER_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );

            controller.addElement(createRightTrigger(
                    1, "RT", -1, controller, context),
                    g.direita(TRIGGER_R_BASE_X + TRIGGER_DISTANCE),
                    g.y(TRIGGER_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LB,
                    ControllerPacket.LB_FLAG, 0, 1, "LB", -1, controller, context),
                    g.esquerda(TRIGGER_L_BASE_X + TRIGGER_DISTANCE),
                    g.y(TRIGGER_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RB,
                    ControllerPacket.RB_FLAG, 0, 1, "RB", -1, controller, context),
                    g.direita(TRIGGER_R_BASE_X),
                    g.y(TRIGGER_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );

            controller.addElement(createLeftStick(controller, context),
                    g.esquerda(ANALOG_L_BASE_X),
                    g.y(ANALOG_L_BASE_Y),
                    g.t(ANALOG_SIZE),
                    g.t(ANALOG_SIZE)
            );

            controller.addElement(createRightStick(controller, context),
                    g.direita(ANALOG_R_BASE_X),
                    g.y(ANALOG_R_BASE_Y),
                    g.t(ANALOG_SIZE),
                    g.t(ANALOG_SIZE)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_BACK,
                    ControllerPacket.BACK_FLAG, 0, 2, "BACK", -1, controller, context),
                    g.centro(BACK_X),
                    g.y(START_BACK_Y),
                    g.t(START_BACK_WIDTH),
                    g.t(START_BACK_HEIGHT)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_START,
                    ControllerPacket.PLAY_FLAG, 0, 3, "START", -1, controller, context),
                    g.centro(START_X),
                    g.y(START_BACK_Y),
                    g.t(START_BACK_WIDTH),
                    g.t(START_BACK_HEIGHT)
            );
        }
        else {
            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_LSB,
                    ControllerPacket.LS_CLK_FLAG, 0, 1, "L3", -1, controller, context),
                    g.esquerda(TRIGGER_L_BASE_X),
                    g.y(L3_R3_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );

            controller.addElement(createDigitalButton(
                    VirtualControllerElement.EID_RSB,
                    ControllerPacket.RS_CLK_FLAG, 0, 1, "R3", -1, controller, context),
                    g.direita(TRIGGER_R_BASE_X + TRIGGER_DISTANCE),
                    g.y(L3_R3_BASE_Y),
                    g.t(TRIGGER_WIDTH),
                    g.t(TRIGGER_HEIGHT)
            );
        }

        if(config.showGuideButton){
            controller.addElement(createDigitalButton(VirtualControllerElement.EID_GDB,
                            ControllerPacket.SPECIAL_BUTTON_FLAG, 0, 1, "GUIDE", -1, controller, context),
                    g.centro(GUIDE_X),
                    g.y(GUIDE_Y),
                    g.t(START_BACK_WIDTH),
                    g.t(START_BACK_HEIGHT)
            );
        }

        controller.setOpacity(config.oscOpacity);
    }

    /**
     * WARP: a mesma grade de 128 x 72 do layout padrão, com a unidade em
     * tamanho físico. O padrão antigo usava 1/72 da altura da tela, e no
     * tablet tudo ficava enorme, tampando o jogo. Aqui a unidade fica perto da
     * de um celular deitado, vezes o tamanho escolhido (pequeno, médio,
     * grande), e nunca maior que a antiga (senão os botões se sobrepõem). Os
     * botões ficam presos às bordas de baixo e dos lados; START, BACK e GUIDE,
     * no centro. Assim o meio da tela fica livre.
     */
    private static final class Grade {
        private final int largura, altura;
        private final float u;

        Grade(DisplayMetrics tela, int tamanhoPct) {
            largura = tela.widthPixels;
            altura = tela.heightPixels;
            float antiga = altura / 72f;
            float fisica = 5.6f * tela.density * tamanhoPct / 100f;
            u = Math.min(antiga, fisica);
        }

        int t(int unidades) {
            return Math.round(unidades * u);
        }

        int esquerda(int x) {
            return Math.round(x * u);
        }

        int direita(int x) {
            return largura - Math.round((128 - x) * u);
        }

        int centro(int x) {
            return Math.round(largura / 2f + (x - 64) * u);
        }

        int y(int yGrade) {
            return altura - Math.round((72 - yGrade) * u);
        }
    }

    public static void saveProfile(final VirtualController controller,
                                   final Context context) {
        SharedPreferences.Editor prefEditor = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE).edit();

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;
            try {
                prefEditor.putString(prefKey, element.getConfiguration().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        prefEditor.apply();
    }

    public static void loadFromPreferences(final VirtualController controller, final Context context) {
        SharedPreferences pref = context.getSharedPreferences(OSC_PREFERENCE, Activity.MODE_PRIVATE);

        for (VirtualControllerElement element : controller.getElements()) {
            String prefKey = ""+element.elementId;

            String jsonConfig = pref.getString(prefKey, null);
            if (jsonConfig != null) {
                try {
                    element.loadConfiguration(new JSONObject(jsonConfig));
                } catch (JSONException e) {
                    e.printStackTrace();

                    // Remove the corrupt element from the preferences
                    pref.edit().remove(prefKey).apply();
                }
            }
        }
    }
}
