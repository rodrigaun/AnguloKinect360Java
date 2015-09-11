package center.rodrigo.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.openni.VideoFrameRef;
import org.openni.VideoStream;

import com.primesense.nite.JointType;
import com.primesense.nite.Point2D;
import com.primesense.nite.SkeletonJoint;
import com.primesense.nite.SkeletonState;
import com.primesense.nite.UserData;
import com.primesense.nite.UserTracker;
import com.primesense.nite.UserTrackerFrameRef;

public class Core extends Component implements VideoStream.NewFrameListener, UserTracker.NewFrameListener {

    private Graphics2D g2;
    private Font font;
    private String angulo;

    /* Esqueleto */
    private UserTracker userTracker;
    private UserTrackerFrameRef lastTrackerFrame;

    private SkeletonJoint sjMaoDireita;
    private SkeletonJoint sjOmbroDireito;
    private SkeletonJoint sjHipDireito;

    private Point2D<Float> pontoMaoDireita;
    private Point2D<Float> pontoOmbroDireito;
    private Point2D<Float> pontoHipDireito;

    /* Camera */
    private VideoStream videoStream;
    private VideoFrameRef lastVideoFrame;
    private BufferedImage bufferedImage;
    private int[] imagePixels;

    public Core(VideoStream videoStream, UserTracker tracker) {

        font = new Font("Arial", Font.BOLD, 26);

        this.userTracker = tracker;
        this.videoStream = videoStream;

        this.userTracker.addNewFrameListener(this);
        this.videoStream.addNewFrameListener(this);
    }

    public synchronized void paint(Graphics g) {

        if (lastVideoFrame == null)
            return;

        if (bufferedImage == null)
            bufferedImage = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);

        bufferedImage.setRGB(0, 0, 640, 480, imagePixels, 0, 640);
        g.drawImage(bufferedImage, 0, 0, null);
        g.setColor(Color.RED);
        g.setFont(font);

        g2 = (Graphics2D) g;
        g2.setStroke(new BasicStroke(7));
        desenhaLinha(g);
    }

    private void desenhaLinha(Graphics g) {

        /* percorre todos os usuarios identificados */
        for (UserData user : lastTrackerFrame.getUsers()) {

            /* verificação se o esqueleto do usuario foi mapeado */
            if (user.getSkeleton().getState() == SkeletonState.TRACKED) {

                /* pego as juncoes necessarias */
                sjMaoDireita = user.getSkeleton().getJoint(JointType.RIGHT_HAND);
                sjOmbroDireito = user.getSkeleton().getJoint(JointType.RIGHT_SHOULDER);
                sjHipDireito = user.getSkeleton().getJoint(JointType.RIGHT_HIP);

                /* verifico se os valores estao zerados */
                if (sjMaoDireita.getPositionConfidence() == 0.0 || sjOmbroDireito.getPositionConfidence() == 0.0)
                    return;

                /* pego os pontos das juncoes */
                pontoMaoDireita = userTracker.convertJointCoordinatesToDepth(sjMaoDireita.getPosition());
                pontoOmbroDireito = userTracker.convertJointCoordinatesToDepth(sjOmbroDireito.getPosition());
                pontoHipDireito = userTracker.convertJointCoordinatesToDepth(sjHipDireito.getPosition());

                g.drawLine(pontoOmbroDireito.getX().intValue(), pontoOmbroDireito.getY().intValue(),
                        pontoMaoDireita.getX().intValue(), pontoMaoDireita.getY().intValue());

                g.drawLine(pontoOmbroDireito.getX().intValue(), pontoOmbroDireito.getY().intValue(), 
                        pontoOmbroDireito.getX().intValue(), pontoHipDireito.getY().intValue());

                angulo = String.format("%.2f", (360 - ((calculaAngulo(pontoOmbroDireito, pontoMaoDireita) * 180) / Math.PI)));
                g.drawString(angulo + "º", (pontoOmbroDireito.getX().intValue() + 15), pontoOmbroDireito.getY().intValue() + 20);
            }
        }
    }

    public double calculaAngulo(Point2D<Float> src, Point2D<Float> dst) {
        double deltaX = dst.getX() - src.getX();
        double deltaY = dst.getY() - src.getY();

        /* destino no canto inferior esquerdo em relação a origem */
        if (deltaX < 0 && deltaY > 0)
            return Math.atan(-deltaX / deltaY);
            
        /* destino no canto superior esquerdo em relação a origem */
        if (deltaX < 0 && deltaY < 0)
            return (Math.PI / 2 + Math.atan(-deltaY / -deltaX));
        
        /* destino no canto inferior direito em relação a origem */
        if (deltaX > 0 && deltaY > 0)
            return (2 * Math.PI - Math.atan(deltaX / deltaY));
        
        /* destino no canto superior direito em relação a origem */
        if (deltaX > 0 && deltaY < 0)
            return (3 * Math.PI / 2 - Math.atan(-deltaY / deltaX));
        
        return 0.0;
    }

    @Override
    public void onFrameReady(VideoStream arg0) {

        lastVideoFrame = videoStream.readFrame();
        ByteBuffer frameData = lastVideoFrame.getData().order(ByteOrder.LITTLE_ENDIAN);

        if (imagePixels == null || imagePixels.length < lastVideoFrame.getWidth() * lastVideoFrame.getHeight())
            imagePixels = new int[lastVideoFrame.getWidth() * lastVideoFrame.getHeight()];

        int pos = 0;
        while (frameData.remaining() > 0) {
            int red = (int) frameData.get() & 0xFF;
            int green = (int) frameData.get() & 0xFF;
            int blue = (int) frameData.get() & 0xFF;
            imagePixels[pos] = 0xFF000000 | (red << 16) | (green << 8) | blue;
            pos++;
        }
        repaint();
    }

    /* Esqueleto */
    @Override
    public synchronized void onNewFrame(UserTracker arg0) {
        
        if (lastTrackerFrame != null) {
            lastTrackerFrame.release();
            lastTrackerFrame = null;
        }
        
        lastTrackerFrame = userTracker.readFrame();

        /* verifica se há um novo usuario */
        for (UserData user : lastTrackerFrame.getUsers()) {
            if (user.isNew()) {
                /* se tem novo usuario, começa a mapear */
                userTracker.startSkeletonTracking(user.getId());
            }
        }
    }
}
