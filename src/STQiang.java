import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/*

Author: Panda
Read readme before use!
This is the main entrance of program with GUI and controls.

 */
public class STQiang {

    private JTextField txtUserName;
    private JTextField txtPassword;
    private JPanel qiangPanel;
    private JTextField txtEventId;
    private JButton btnSchedule;
    private JButton btnCancel;
    private JTextArea statusArea;
    private JLabel lblStar;
    private JTextField txtName;
    private JTextField txtTicketSale;
    private JLabel lblTicketSale;
    private StatusUpdater updater;

    private Timer timer;
    private Timer warningTimer;


    public STQiang() {
        initGUI();
    }

    public void initGUI() {

        updater = new StatusUpdater();
        DefaultCaret caret = (DefaultCaret)statusArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        statusArea.setEditable(false);
        btnCancel.setEnabled(false);
        txtTicketSale.setVisible(false);
        lblTicketSale.setVisible(false);

        /* Secret Debug Entrance */
        lblStar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Thread testQiang = new Thread(new QiangTask());
                testQiang.start();
            }
        });

        btnSchedule.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if(!validateInput()) return ;

                Thread qiangThread = new Thread(new Runnable() {
                    @Override
                    public void run() {

                        btnSchedule.setEnabled(false);

                        TicketSwallower ticketSwallower = new TicketSwallower(txtUserName.getText(),
                                txtPassword.getText(), txtName.getText(), txtEventId.getText(), updater);

                        try {
                            if(!ticketSwallower.testAuth()) {
                                JOptionPane.showMessageDialog(qiangPanel,
                                        "Login Failed. Invalid username or password.",
                                        "Authentication Error",
                                        JOptionPane.ERROR_MESSAGE);
                                return ;
                            }
                            else {

                                Date targetDate = getQiangDate();
                                warningTimer = new Timer();
                                warningTimer.scheduleAtFixedRate(new TimerTask() {
                                    @Override
                                    public void run() {
                                        Date targetDate = getQiangDate();
                                        long remaint = (targetDate.getTime() - Calendar.getInstance().getTime().getTime()) / 1000;

                                        if(remaint <= 0) {
                                            warningTimer.cancel();
                                            lblStar.setText("抢");
                                        }

                                        lblStar.setText(remaint + "s");
                                        lblStar.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 75));
                                    }
                                }, 300, 1000);

                                System.out.println(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(targetDate));

                                timer = new Timer();
                                timer.schedule(new QiangTask(), targetDate);          //Schedule Task



                                //Set GUI
                                txtTicketSale.setVisible(true);
                                lblTicketSale.setVisible(true);

                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd EEE HH:mm");
                                txtTicketSale.setText(dateFormat.format(targetDate));

                                txtPassword.setEnabled(false);
                                txtUserName.setEnabled(false);
                                txtName.setEnabled(false);
                                txtEventId.setEnabled(false);
                                btnCancel.setEnabled(true);

                            }

                        } catch(IOException e) {
                            e.printStackTrace();
                            JOptionPane.showMessageDialog(qiangPanel,
                                    "ERROR: could not establish connection to remote server.",
                                    "Connection Error",
                                    JOptionPane.ERROR_MESSAGE);
                            return ;
                        }

                    }

                });
                qiangThread.start();

            }
        });

        btnCancel.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                timer.cancel();
                txtPassword.setEnabled(true);
                txtUserName.setEnabled(true);
                txtName.setEnabled(true);
                txtEventId.setEnabled(true);
                btnSchedule.setEnabled(true);
                btnCancel.setEnabled(false);
                warningTimer.cancel();
                lblStar.setText("抢");

            }

        });

    }

    private static Date getQiangDate() {

        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        int weekDay = calendar.get(Calendar.DAY_OF_WEEK);
        int days = 0;

        if(weekDay != Calendar.MONDAY) {
            days = (7 + Calendar.MONDAY - weekDay) % 7;
        }

        calendar.add(Calendar.DATE, days);
        return calendar.getTime();

    }

    private boolean validateInput() {

        if(txtEventId.getText().equals("")) {
            JOptionPane.showMessageDialog(qiangPanel, "Please choose an event!");
            return false;

        }
        else if(txtUserName.getText().equals("") || txtPassword.getText().equals("")) {
            JOptionPane.showMessageDialog(qiangPanel, "Please enter credentials for Haverford Account");
            return false;
        }
        return true;
    }

    /**
     * Class for Engine/GUI communication. Consider better design pattern if possible.
     */
    public class StatusUpdater {

        public void updateStatus(String status) {
            statusArea.setText(statusArea.getText() + "\n" + status);
        }

    }


    private class QiangTask extends TimerTask {

        @Override
        public void run() {

            TicketSwallower ticketSwallower = new TicketSwallower(txtUserName.getText(),
                    txtPassword.getText(), txtName.getText(), txtEventId.getText(), updater);
            int status;
            try {
                status = ticketSwallower.startEngine();
            } catch(IOException e) {
                status = -3;
            }
            if(status == -1)   {
                updater.updateStatus("Login Failed: Invalid username and password");
                JOptionPane.showMessageDialog(qiangPanel,
                        "Login Failed. Invalid username or password.",
                        "Authentication Error",
                        JOptionPane.ERROR_MESSAGE);
            }
            else if(status == -2) {
                updater.updateStatus("Reservation Failed: You already have a ticket.");
                JOptionPane.showMessageDialog(qiangPanel,
                        "You already have a ticket!!!!!",
                        "Greedy Human",
                        JOptionPane.ERROR_MESSAGE);

            }
            else if(status == -3) {

                updater.updateStatus("Connection Error. Contact Cornie.");
                JOptionPane.showMessageDialog(qiangPanel,
                        "Remote Connection Error. Contact Cornie!!",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE);


            }
            else if(status == 0) {
                JOptionPane.showMessageDialog(qiangPanel,
                        "Ticket SWALLOWED. Enjoy!!!!",
                        "Qiang!! Qiang!! Qiang!!",
                        JOptionPane.INFORMATION_MESSAGE);

            }
            else {
                JOptionPane.showMessageDialog(qiangPanel,
                        "What happens??",
                        "Something Happens",
                        JOptionPane.ERROR_MESSAGE);

            }

        }
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("ST! Qiang 0.12 beta4 No Paper Due Edition");
        frame.setContentPane(new STQiang().qiangPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.pack();
        frame.setVisible(true);

    }


}
