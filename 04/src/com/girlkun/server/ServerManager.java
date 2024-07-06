package com.girlkun.server;

import com.girlkun.MaQuaTang.MaQuaTangManager;
import com.girlkun.database.GirlkunDB;

import java.net.ServerSocket;

import com.girlkun.jdbc.daos.HistoryTransactionDAO;
import com.girlkun.models.boss.BossManager;
import com.girlkun.models.item.Item;
import com.girlkun.models.map.challenge.MartialCongressManager;
import com.girlkun.models.matches.pvp.DaiHoiVoThuat;
import com.girlkun.models.player.Player;
import com.girlkun.network.session.ISession;
import com.girlkun.network.example.MessageSendCollect;
import com.girlkun.network.server.GirlkunServer;
import com.girlkun.network.server.IServerClose;
import com.girlkun.network.server.ISessionAcceptHandler;
import com.girlkun.server.io.MyKeyHandler;
import com.girlkun.server.io.MySession;
import com.girlkun.services.ClanService;
import com.girlkun.services.InventoryServiceNew;
import com.girlkun.services.NgocRongNamecService;
import com.girlkun.services.Service;
import com.girlkun.services.func.ChonAiDay;
import com.girlkun.services.func.TopService;
import com.girlkun.utils.Logger;
import com.girlkun.utils.TimeUtil;
import com.girlkun.utils.Util;
import com.girlkun.kygui.ShopKyGuiManager;
import com.girlkun.jdbc.daos.PlayerDAO;
import com.girlkun.models.map.daihoi.DaiHoiService;
import com.girlkun.models.map.sieuhang.SieuHangManager;
import com.girlkun.models.map.vodai.MartialCongressManagere;
import com.girlkun.models.npc.NauBanh;
import com.girlkun.result.GirlkunResultSet;
import com.girlkun.services.func.TaiXiu;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JPanel;
//import com.girlkun.models.boss.list_boss.yadart.BossDao;

public class ServerManager {

    public static String timeStart;
    public int threadMap;

    public static final Map CLIENTS = new HashMap();

    public static String NAME = "Girlkun75";
    public static int PORT = 14445;

    private static ServerManager instance;

    public static ServerSocket listenSocket;
    public static boolean isRunning;

    public void init() {
        Manager.gI();
        try {
            if (Manager.LOCAL) {
                return;
            }
            GirlkunDB.executeUpdate("update account set last_time_login = '2000-01-01', "
                    + "last_time_logout = '2001-01-01'");
        } catch (Exception e) {
        }
        HistoryTransactionDAO.deleteHistory();
    }

    public static ServerManager gI() {
        if (instance == null) {
            instance = new ServerManager();
            instance.init();
        }
        return instance;
    }

    public static void main(String[] args) {
        timeStart = TimeUtil.getTimeNow("dd/MM/yyyy HH:mm:ss");
//        ServerManager.gI().run();
        new com.girlkun.server.barcoll().setVisible(true);
    }

    public void run() {
        long delay = 500;
        isRunning = true;
        new Thread(() -> {
            while (true) {
                try {
                    SieuHangManager.gI().update();
                    Thread.sleep(50);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        activeGame();
        activeServerSocket();
        Logger.log(Logger.RED, "Run Server thành công\n");
        new Thread(DaiHoiVoThuat.gI(), "Thread DHVT").start();

        ChonAiDay.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
        new Thread(ChonAiDay.gI(), "Thread CAD").start();

        NgocRongNamecService.gI().initNgocRongNamec((byte) 0);

        new Thread(NgocRongNamecService.gI(), "Thread NRNM").start();

        new Thread(NauBanh.gI(), "Thread NauBanh").start();

        new Thread(TopService.gI(), "Thread TOP").start();
        TaiXiu.gI().lastTimeEnd = System.currentTimeMillis() + 50000;
        new Thread(TaiXiu.gI(), "Thread TaiXiu").start();
        new Thread(() -> {
            while (isRunning) {
                try {
                    long start = System.currentTimeMillis();
                    MartialCongressManager.gI().update();
                    MartialCongressManagere.gI().update();
                    Player player = null;
                    for (int i = 0; i < Client.gI().getPlayers().size(); ++i) {
                        if (Client.gI().getPlayers().get(i) != null) {
                            player = (Client.gI().getPlayers().get(i));
                            PlayerDAO.updatePlayer(player);
                        }
                    }
                    ShopKyGuiManager.gI().save();
                    long timeUpdate = System.currentTimeMillis() - start;
                    if (timeUpdate < delay) {
                        Thread.sleep(delay - timeUpdate);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Update dai hoi vo thuat").start();
        try {
            Thread.sleep(1000);
            BossManager.gI().loadBoss();
            Manager.MAPS.forEach(com.girlkun.models.map.Map::initBoss);
            DaiHoiService.gI().initDaiHoiVoThuat();

        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(BossManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void act() throws Exception {
        GirlkunServer.gI().init().setAcceptHandler(new ISessionAcceptHandler() {
            @Override
            public void sessionInit(ISession is) {
//                antiddos girlkun
                if (!canConnectWithIp(is.getIP())) {
                    is.disconnect();
                    return;
                }

                is = is.setMessageHandler(Controller.getInstance())
                        .setSendCollect(new MessageSendCollect())
                        .setKeyHandler(new MyKeyHandler())
                        .startCollect();
            }

            @Override
            public void sessionDisconnect(ISession session) {
                Client.gI().kickSession((MySession) session);
            }
        }).setTypeSessioClone(MySession.class)
                .setDoSomeThingWhenClose(new IServerClose() {
                    @Override
                    public void serverClose() {
                        System.out.println("server close");
                        System.exit(0);
                    }
                })
                .start(PORT);

    }

    private void activeServerSocket() {
        if (true) {
            try {
                this.act();
            } catch (Exception e) {
            }
            return;
        }
//        try {
//            Logger.log(Logger.PURPLE, "Start server......... Current thread: " + Thread.activeCount() + "\n");
//            listenSocket = new ServerSocket(PORT);
//            while (isRunning) {
//                try {
//                    Socket sc = listenSocket.accept();
//                    String ip = (((InetSocketAddress) sc.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
//                    if (canConnectWithIp(ip)) {
//                        Session session = new Session(sc, ip);
//                        session.ipAddress = ip;
//                    } else {
//                        sc.close();
//                    }
//                } catch (Exception e) {
////                        Logger.logException(ServerManager.class, e);
//                }
//            }
//            listenSocket.close();
//        } catch (Exception e) {
//            Logger.logException(ServerManager.class, e, "Lỗi mở port");
//            System.exit(0);
//        }
    }

    private boolean canConnectWithIp(String ipAddress) {
        Object o = CLIENTS.get(ipAddress);
        if (o == null) {
            CLIENTS.put(ipAddress, 1);
            return true;
        } else {
            int n = Integer.parseInt(String.valueOf(o));
            if (n < Manager.MAX_PER_IP) {
                n++;
                CLIENTS.put(ipAddress, n);
                return true;
            } else {
                return false;
            }
        }
    }

    public void disconnect(MySession session) {
        Object o = CLIENTS.get(session.getIP());
        if (o != null) {
            int n = Integer.parseInt(String.valueOf(o));
            n--;
            if (n < 0) {
                n = 0;
            }
            CLIENTS.put(session.getIP(), n);
        }
    }

    private void activeCommandLine() {
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (true) {
                String line = sc.nextLine();
                if (line.equals("baotri")) {
                    Maintenance.gI().start(15);
                } else if (line.equals("baotrinhanh")) {
                    Maintenance.gI().start(0);
                } else if (line.equals("athread")) {
                    ServerNotify.gI().notify("Nro Mới debug server: " + Thread.activeCount());
                } else if (line.equals("nplayer")) {
                    Logger.error("Player in game: " + Client.gI().getPlayers().size() + "\n");
                } else if (line.equals("admin")) {
                    new Thread(() -> {
                        Client.gI().close();
                    }).start();
                } else if (line.startsWith("bang")) {
                    new Thread(() -> {
                        try {
                            ClanService.gI().close();
                            Logger.error("Save " + Manager.CLANS.size() + " bang");
                        } catch (Exception e) {
                            Logger.error("Lỗi save clan!...................................\n");
                        }
                    }).start();
                } else if (line.startsWith("a")) {
                    String a = line.replace("a ", "");
                    Service.gI().sendThongBaoAllPlayer(a);
                } else if (line.startsWith("qua")) {
//                    =1-1-1-1=1-1-1-1=
//                     =playerId-quantily-itemId-sql=optioneId-pagram=

                    try {
                        List<Item.ItemOption> ios = new ArrayList<>();
                        String[] pagram1 = line.split("=")[1].split("-");
                        String[] pagram2 = line.split("=")[2].split("-");
                        if (pagram1.length == 4 && pagram2.length % 2 == 0) {
                            Player p = Client.gI().getPlayer(Integer.parseInt(pagram1[0]));
                            if (p != null) {
                                for (int i = 0; i < pagram2.length; i += 2) {
                                    ios.add(new Item.ItemOption(Integer.parseInt(pagram2[i]), Integer.parseInt(pagram2[i + 1])));
                                }
                                Item i = Util.sendDo(Integer.parseInt(pagram1[2]), Integer.parseInt(pagram1[3]), ios);
                                i.quantity = Integer.parseInt(pagram1[1]);
                                InventoryServiceNew.gI().addItemBag(p, i);
                                InventoryServiceNew.gI().sendItemBags(p);
                                Service.gI().sendThongBao(p, "Admin trả đồ. anh em thông cảm nhé...");
                            } else {
                                System.out.println("Người chơi không online");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Lỗi quà");
                    }
                } else if (line.equals("bkt")) {
                    System.gc();
                    System.err.println("Clean.........");
                }
            }
        }, "Active line").start();
    }

    private void activeGame() {
    }

    public void close(long delay) {
        GirlkunServer.gI().stopConnect();

        isRunning = false;
        try {
            ClanService.gI().close();
        } catch (Exception e) {
            Logger.error("Lỗi save clan!...................................\n");
        }
        Client.gI().close();

        Logger.success("SUCCESSFULLY MAINTENANCE!...................................\n");
        if (barcoll.isRunning) {
            barcoll.isRunning = false;
            try {
                String batchFilePath = "run.bat";
                barcoll.runBatchFile(batchFilePath);
            } catch (IOException e) {
            }
        }
        System.exit(0);
    }

    public long getNumPlayer() {
        long num = 0;
        try {
            GirlkunResultSet rs = GirlkunDB.executeQuery("SELECT COUNT(*) FROM `player`");
            rs.first();
            num = rs.getLong(1);
        } catch (Exception e) {
        }
        return num;
    }
}
