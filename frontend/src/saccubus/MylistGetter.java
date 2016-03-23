package saccubus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import saccubus.json.Mson;
import saccubus.net.Gate;
import saccubus.net.Loader;
import saccubus.net.Path;

//import saccubus.util.Stopwatch;

/**
 * MylistGetter urlのマイリストをネットから読み込んで 動画ID_title<改行> のリスト文字列を返す
 * extends SwingWorker<String,String>
 * @author orz
 *
 */
public class MylistGetter extends SwingWorker<String, String> {

	private static final boolean MYLIST_DEBUG = false;
	private static final boolean MYLIST_DEBUG2 = false;
	private String url;
	private final MainFrame parent;
	private final JLabel[] status3;
	private JLabel Status;
//	private JLabel MovieInfo;
	private final ConvertStopFlag StopFlag;
	private final StringBuffer ret;

	private final ConvertingSetting Setting;
	private String resultText;
	private String mylistID;
	private String watchInfo;
//	private String Tag;
	private Gate gate;
	private int id;

	public MylistGetter(int worker_id, String url0, MainFrame frame,
		JLabel[] in_status,ConvertStopFlag flag, StringBuffer sb)
	{
		id = worker_id;
		url = url0;
		int index = url.indexOf('?');
		if(index >= 0){
		//	int index2 = url.lastIndexOf('/',index);
		//	Tag = url.substring(index2+1,index);
			watchInfo = url.substring(index);
		}else{
		//	int index2 = url.lastIndexOf('/');
		//	Tag = url.substring(index2+1);
			watchInfo = "";
		}
		parent = frame;
		status3 = in_status;
		Status = status3[0];
		StopFlag = flag;
		Setting = frame.getSetting();
		ret = sb;
	}

	public MylistGetter(int worker_id, String tag, String info, ConvertingSetting setting,
			JLabel[] in_status, ConvertStopFlag flag, StringBuffer sb)
	{
		id = worker_id;
		url = tag;
		watchInfo = info;
		parent = null;
		status3 = in_status;
		Status = status3[0];
		StopFlag = flag;
		Setting = setting;
		ret = sb;
	}

	//	private void sendtext(String text) {
//		Status.setText(text);
//	}

	private void sendtext(String text) {
	//	Status.setText(text);
		publish(text);
	}

	protected void process(List<String> lists){
	//	Status.setText(text);
		String text = "";
		for(String s:lists) text = s;
		Status.setText(text);
	}

	@SuppressWarnings("unused")
	@Override
	protected String doInBackground() throws Exception {
		if(!url.startsWith("http")){
			// return ret unchanged
			return "00";
		}
		final ArrayList<String[]> plist = new ArrayList<String[]>();
		//start here.
		Path file = Path.mkTemp(url.replace("http://","").replace("nicovideo.jp/","")
				.replaceAll("[/\\:\\?=\\&]+", "_") + ".html");
		Loader loader = new Loader(Setting, status3);
		gate = Gate.open(id);
		if(!loader.load(url,file)){
			sendtext("[E1]load失敗 "+url);
			gate.exit("E1");
			return "E1";
		}
		gate.exit(0);
		String text = Path.readAllText(file.getPath(), "UTF-8");
		sendtext("保存しました。" + file.getRelativePath());
		if(StopFlag.needStop()) {
			return "FF";
		}
		if(MYLIST_DEBUG && parent!=null){
			resultText = HtmlView.markupHtml(text);
			final HtmlView hv = new HtmlView(parent, "マイリスト", url);
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					hv.setText(resultText);
				}
			});
		}
		if(StopFlag.needStop()) {
			return "FF";
		}
		if(url.contains("api/deflist")) {
			//mylist api処理
			mylistID = "deflist";
		}else
		if(url.contains("api/mylist/list?group_id=")){
			//mylist api処理
			int start = url.indexOf("id=")+3;
			mylistID = url.substring(start);
		}else
		if(url.contains("mylist")) {
			//mylist処理
			String json_start = "Mylist.preload(";
			int start = text.indexOf(json_start);
			if(start < 0){
				sendtext("JSON not found "+url);
				return "E2";	//JSON not found
			}
			start += json_start.length();
			int end = (text+");\n").indexOf(");\n", start);	// end of JSON
			text = (text+");\n").substring(start, end);
			start = text.indexOf(",");
			mylistID = text.substring(0, start);
			text = text.substring(start+1).trim();
		}else{
			mylistID = "0";	//not implemented
			// here will come XML parser
			// watch/(sm|nm|so)?9999 を抽出してautolist0.txtに出力する
			HashSet<String> set = new HashSet<String>();
			String regex = "watch/(sm|nm|so)?[1-9][0-9]*";
			Pattern p = Pattern.compile(regex);
			Matcher m = p.matcher(text);
			String s = "";
			while(m.find()){
				s = text.substring(m.start(),m.end());
				s = s.replace("watch/", "");
				if(MainFrame.idcheck(s)){
					set.add(s);
				}
			}
			sendtext("抽出成功 "+mylistID + "　"+set.size()+"個　"+ url);
			if(set.isEmpty()){
				sendtext("[E5]動画がありません");
				return "E5";
			}
			// file output
			Iterator<String> it = set.iterator();
			while(it.hasNext()){
				String[] sary = new String[1];
				sary[0] = it.next();
				plist.add(sary);
			}
			if(Setting.isSaveAutoList()){
				if(saveAutoList("autolist0",plist)){
					sendtext("[00]autolist0"+".bat 出力成功");
					return "00";
				}else{
					//出力失敗
					sendtext("[E6]autolist0"+".bat 出力失敗");
					return "E6";
				}
			} else {
				//結果データ
				for(String[] vals:plist){
					if(vals.length>1)
						ret.append(vals[0]+"\t"+vals[1]+"\t"+watchInfo+"\n");
					else
						ret.append(vals[0]+"\tlist0\t"+watchInfo+"\n");
				}
				return "00";
			}
		}
		//common
		try {

				file = new Path(file.getRelativePath().replace(".html", ".xml"));
				Path.unescapeStoreXml(file, text, url);		//xml is property key:json val:JSON
				Properties prop = new Properties();
					prop.loadFromXML(new FileInputStream(file));		//read JSON xml
				text = prop.getProperty("json", "0");
				file = new Path(file.getRelativePath().replace(".html", ".xml"));
				//
				if(MYLIST_DEBUG && parent!=null){
					resultText = HtmlView.markupHtml(text);
					final HtmlView hv2 = new HtmlView(parent, "マイリスト mson", "mson");
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							hv2.setText(resultText);
						}
					});
				}
				//
				System.out.println("get mylist/"+mylistID);
				System.out.println("mson: "+text.length());
				if(StopFlag.needStop()) {
					return "FF";
				}
				// parse mson
				sendtext("パース実行中");
				Mson mson = null;
				try{
					mson = Mson.parse(text);
				}catch(Exception e){
					e.printStackTrace();
				}
				if(mson==null){
					sendtext("[E3]パース失敗");
					return "E3";
				}
				sendtext("パース成功 "+mylistID);
				if(StopFlag.needStop()) {
					return "FF";
				}
				//rename to .txt
				file = new Path(file.getRelativePath().replace(".xml", ".txt"));
				mson.prettyPrint(new PrintStream(file));	//pretty print
				sendtext("リスト成功 "+mylistID);
				if(StopFlag.needStop()) {
					return "FF";
				}
				String[] keys = {"watch_id","title"};
				ArrayList<String[]> id_title_list = mson.getListString(keys);	// List of id & title
				for(String[] vals:id_title_list){
				//	System.out.println("Getting ["+ vals[0] + "]"+ vals[1]);
					plist.add(0, vals);
				}

				int sz = plist.size();
				sendtext("抽出成功 "+mylistID + "　"+sz+"個　"+ url);
				System.out.println("Success mylist/"+mylistID+" item:"+sz);
				if(sz == 0){
					sendtext("[E4]動画がありません。"+mylistID);
					return "E4";
				}
				if(StopFlag.needStop()) {
					return "FF";
				}
				if(MYLIST_DEBUG2 && parent!=null){
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							TextView dlg = new TextView(parent, "mylist/"+mylistID);
							JTextArea textout = dlg.getTextArea();
							for(String[] idts:plist){
								textout.append("["+idts[0]+"]"+idts[1]+"\n");
							}
							textout.setCaretPosition(0);
						}
					});
				}

				if(StopFlag.needStop()) {
					return "FF";
				}
				//start downloader
				if(Setting.isSaveAutoList()){
					if(saveAutoList("autolist",plist)){
						sendtext("[00]autolist.bat 出力成功");
						return "00";
					}else{
					sendtext("[E6]autolist.bat 出力失敗");
						return "E6";
					}
				} else {
					//結果データ
					for(String[] vals:plist){
						if(vals.length>1)
							ret.append(vals[0]+"\t"+vals[1]+"\t"+watchInfo+"\n");
						else
							ret.append(vals[0]+"\tlist0\t"+watchInfo+"\n");
					}
					return "00";
				}
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally{

		}
		sendtext("[EX]例外発生?");
		return "EX";
	}

	//終了時EDTで自動実行される
	public void done(){
		String result = null;
		try {
			result = get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		StopFlag.finish();
		StopFlag.setButtonEnabled(false);
		if (result==null){
			System.out.println("done#get()==null バグ?");
		}else{
			System.out.println("done#get() "+result);
		}
		if(parent!=null)
			parent.myListGetterDone(ret);
	}

	private boolean saveAutoList(String listname, ArrayList<String[]> mylist) {
		File autobat = new File(".\\auto.bat");
		final String CMD_LINE = "%CMD% ";
		File autolist = new File(".\\autolist.bat");
		if(!autobat.canRead()){
			System.out.println("auto.batがないのでautolist.batが出力できません:"+mylistID);
			sendtext("出力失敗 autolist.bat:"+mylistID);
			return false;
		}
		//
		String user = Setting.getMailAddress();
		if(user!=null && user.isEmpty()) user = null;
		BufferedReader br = null;
		PrintWriter pw = null;
		String s;
		boolean flag2nd = false;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(autobat), "MS932"));
			pw = new PrintWriter(autolist, "MS932");
			pw.println(":自動生成 autolist.bat for mylist/" + mylistID);
			pw.println(": produced by Saccubus" + MainFrame_AboutBox.rev + " " + new Date());
			pw.println(":――――――――――――――――――");
			while((s = br.readLine())!=null){
				if(s.startsWith("set MAILADDR=doremi@mahodo.co.jp") && user!=null){
					// メールアドレス置換
					pw.println("set MAILADDR="+user);
					continue;
				}
				if(s.startsWith("set PASSWORD=steeki_tabetai") && user!=null){
					// パスワード置換
					pw.println("set PASSWORD=!");
					continue;
				}
				if(!s.startsWith(CMD_LINE)){
					// %CMD%行が現れるまでコピー
					pw.println(s);
				}else{
					// マイリストの%CMD%出力
					pw.println(":――――――――――――――――――");
					pw.println(":set OPTION=過去ログ日時 他のオプション などを必要に応じ指定(readmeNew.txt参照)");
					pw.println("set OPTION=");
					watchInfo = watchInfo.replace('?', '&');
					if(!s.contains("auto")){
						// マイリストの%CMD%出力(記述法1)
						pw.println(":保存変換しない行は削除してください");
						for(String[] ds: mylist){
							if(ds.length>1){
								pw.println(":タイトル " + ds[1]);
							}
							pw.println("%CMD% "+ ds[0] + "?watch_harmful=1"+watchInfo+" %OPTION% @PUP");
						}
					}else{
						// マイリストの%CMD%出力(記述法2)
						flag2nd = true;
						pw.print("%CMD% autolist?watch_harmful=1"+watchInfo+" %OPTION% @PUP");
					}
					break;
				}
			}
			while((s = br.readLine())!=null){
				if(!s.startsWith(CMD_LINE)){
					// %CMD%行以外を出力
					pw.println(s);
					continue;
				}
			}
			sendtext("出力成功 autolist.bat:"+mylistID);
			if(!flag2nd)
				return true;
			else
				return saveListtxt(".\\autolist.txt",mylist);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try{
				br.close();
				pw.flush();
				pw.close();
			}catch(Exception ex){
				ex.printStackTrace();
			}
		}
		return false;
	}

	boolean saveListtxt(String filename,List<String[]> dl){
		// 記述法2 autolist.txt出力
		File autolisttxt = new File(filename);
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(autolisttxt, "MS932");
			pw.println(":自動生成 autolist.txt for mylist/" + mylistID);
			pw.println(": produced by Saccubus" + MainFrame_AboutBox.rev + " " + new Date());
			pw.println(":保存変換しない行は削除してください");
			for(String[] ds: dl){
				if(ds.length>1)
					pw.println(ds[0] + "\tタイトル :" + ds[1]);
				else
					pw.println(ds[0]);
			}
			sendtext("出力成功 autolist.txt:"+mylistID);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (pw!=null) {
				try{
					pw.flush();
					pw.close();
				}catch(Exception ex){
					ex.printStackTrace();
				}
			}
		}
		return false;
	}
}
