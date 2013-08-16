package ch.ethz.fcl.mogl.mapping;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import ch.ethz.fcl.mogl.gl.ProjectionUtils;
import ch.ethz.fcl.mogl.scene.AbstractTool;
import ch.ethz.fcl.mogl.scene.IView;
import ch.ethz.fcl.mpm.View;
import ch.ethz.fcl.util.PreferencesStore;

public final class CalibrationTool extends AbstractTool {
	public static final double MAX_CALIBRATION_ERROR = 0.5;

	private final ICalibrator calibrator = new BimberRaskarCalibrator();
	private final ICalibrationModel model;
	
	private Map<IView, CalibrationContext> contexts = new HashMap<IView, CalibrationContext>();

	public CalibrationTool(ICalibrationModel model) {
		this.model = model;
	}
	
	@Override
	public void keyPressed(KeyEvent e, IView view) {
		switch (e.getKeyCode()) {
		case KeyEvent.VK_C:
			contexts.put(view, new CalibrationContext());
			break;
		case KeyEvent.VK_L:
			loadCalibration(view);
			break;
		case KeyEvent.VK_S:
			saveCalibration(view);
			break;
		case KeyEvent.VK_UP:
			cursorAdjust(view, 0, 1);
			break;
		case KeyEvent.VK_DOWN:
			cursorAdjust(view, 0, -1);
			break;
		case KeyEvent.VK_LEFT:
			cursorAdjust(view, -1, 0);
			break;
		case KeyEvent.VK_RIGHT:
			cursorAdjust(view, 1, 0);
			break;
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_DELETE:
			deleteCurrent(view);
			break;
		default:
			super.keyPressed(e, view);
		}
		view.getScene().repaintAll();
	}
	
	@Override
	public void mousePressed(MouseEvent e, IView view) {
		CalibrationContext context = getContext(view);

		// reset first
		context.currentSelection = -1;

		// first, try to hit calibration point
		for (int i = 0; i < context.projectedVertices.size(); ++i) {
			int x = ProjectionUtils.deviceToScreenX(view, context.projectedVertices.get(i).getX());
			int y = ProjectionUtils.deviceToScreenY(view, context.projectedVertices.get(i).getY());
			if (snap2D(e.getX(), view.getHeight() - e.getY(), x, y)) {
				// we got a point to move!
				context.currentSelection = i;
				view.repaint();
				return;
			}
		}

		// second, try to hit model point
		float[] mv = model.getCalibrationVertices();
		double[] vv = new double[3];
		for (int i = 0; i < mv.length; i += 3) {
			if (!ProjectionUtils.projectToScreenCoordinates(view, mv[i], mv[i + 1], mv[i + 2], vv))
				continue;
			if (snap2D(e.getX(), view.getHeight() - e.getY(), (int) vv[0], (int) vv[1])) {
				Vector3D a = new Vector3D(mv[i], mv[i + 1], mv[i + 2]);
				int index = context.modelVertices.indexOf(a);
				if (index != -1) {
					context.currentSelection = index;
				} else {
					context.currentSelection = context.modelVertices.size();
					context.modelVertices.add(a);
					context.projectedVertices.add(new Vector3D(ProjectionUtils.screenToDeviceX(view, (int) vv[0]), ProjectionUtils.screenToDeviceY(view, (int) vv[1]), 0));
				}
				calibrate(view);
				view.repaint();
				return;
			}
		}	}
	
	@Override
	public void mouseDragged(MouseEvent e, IView view) {
		CalibrationContext context = getContext(view);
		if (context.currentSelection != -1) {
			Vector3D a = new Vector3D(ProjectionUtils.screenToDeviceX(view, e.getX()), ProjectionUtils.screenToDeviceY(view, view.getHeight() - e.getY()), 0);
			context.projectedVertices.set(context.currentSelection, a);
			calibrate(view);
		}
		view.repaint();
	}
	
	private CalibrationContext getContext(IView view) {
		CalibrationContext context = contexts.get(view);
		if (context == null) {
			context = new CalibrationContext();
			contexts.put(view, context);
		}
		return context;
	}
	
	private void cursorAdjust(IView view, double dx, double dy) {
		CalibrationContext context = getContext(view);
		if (context.currentSelection != -1) {
			Vector3D p = context.projectedVertices.get(context.currentSelection);
			Vector3D a = new Vector3D(p.getX() + dx / view.getWidth(), p.getY() + dy / view.getHeight(), 0.0);
			context.projectedVertices.set(context.currentSelection, a);
			calibrate(view);
		}
	}

	private void deleteCurrent(IView view) {
		CalibrationContext context = getContext(view);
		if (context.currentSelection != -1) {
			context.modelVertices.remove(context.currentSelection);
			context.projectedVertices.remove(context.currentSelection);
			context.currentSelection = -1;
			calibrate(view);
		}
	}

	private void loadCalibration(IView view) {
		Preferences p = PreferencesStore.get();
		int iv = 0;
		for (IView v : view.getScene().getViews()) {
			getContext(v).load(p, iv);
			calibrate(v);
			iv++;
		}
	}

	private void saveCalibration(IView view) {
		Preferences p = PreferencesStore.get();
		int iv = 0;
		for (IView v : view.getScene().getViews()) {
			getContext(v).save(p, iv);
			iv++;
		}
	}

	private void calibrate(IView view) {
		try {
			CalibrationContext context = getContext(view);
			context.calibrated = false;

			double error = calibrator.calibrate(context.modelVertices, context.projectedVertices, View.NEAR, View.FAR);
			if (error < MAX_CALIBRATION_ERROR)
				context.calibrated = true;

			view.setProjectionMatrix(calibrator.getProjectionMatrix());
			view.setModelviewMatrix(calibrator.getModelviewMatrix());
			view.repaint();
			// System.out.println("error: " + error);
		} catch (Throwable t) {
		}
	}	
}
