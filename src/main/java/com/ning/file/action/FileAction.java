package com.ning.file.action;

import com.ning.exception.file.FileException;
import com.ning.exception.login.LoginException;
import com.ning.file.entity.History;
import com.ning.file.entity.OrderInfo;
import com.ning.file.service.FileService;
import com.ning.login.entity.User;
import com.ning.login.service.UserService;
import com.ning.util.properties.PropertiesUtil;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author wangn
 * @date 2017/5/19
 */
@Controller
public class FileAction {
    @Resource
    private UserService userService;

    @Resource
    private FileService fileService;

    //自定义类型转换器
    @InitBinder
    public void initBinder(WebDataBinder binder) throws Exception {
        binder.registerCustomEditor(Date.class, new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"), true));
    }

    //文件上传主页入口方法
    @RequestMapping("fileupload")
    public String index(Model model) throws Exception {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        if (user.getPercode().equals("admin")) {
            return "admin";
        }
        //用户上传历史实体
        List<History> userHistoryList = fileService.getUpListByUID(user.getUid());
        for (History history : userHistoryList) {
            OrderInfo orderInfo = fileService.getOrderInfoEntityByOID(history.getHoid());
            if (orderInfo != null) {
                history.setOsubject(orderInfo.getOsubject());
                history.setOname(orderInfo.getOname());
                //设置文件扩展名
                history.setFilepath(history.getFilepath().substring(history.getFilepath().lastIndexOf(".") + 1));
            }
        }
        //下拉框数据
        model.addAttribute("orderInfoList", fileService.getOrderInfoEntity());
        model.addAttribute("user", user);
        model.addAttribute("userHistoryList", userHistoryList);
        boolean firstLogin = userService.isFirstLogin(user.getUid());
        if (firstLogin) {
            return "jsp/firstpd.jsp";
        }
        return "jsp/fileupload.jsp";
    }

    /**
     * 更改密码方法
     *
     * @param model
     * @param password
     * @param firstlogin
     * @param session
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "changePassword", method = RequestMethod.POST)
    public String changePassword(Model model, String password, String firstlogin, HttpSession session) throws Exception {
        if (password == null || "".equals(password)) {
            throw new LoginException("修改密码失败：参数为空");
        }
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        if (password.length() < 8) {
            model.addAttribute("user", user);
            model.addAttribute("errorinfo", "密码不能小于8位！");
            if (firstlogin != null && "1".equals(firstlogin)) {
                return "jsp/firstpd.jsp";
            }
            return "jsp/cpasswd.jsp";
        }
        String uid = user.getUid();
        Map<String, String> map = new HashMap<String, String>();
        map.put("uid", uid);
        map.put("password", password);
        String passwdById = userService.getPasswdById(uid);
        if (passwdById.equals(password)) {
            model.addAttribute("errorinfo", "新密码不能和原密码相同，请重新输入！");
            if (firstlogin != null && "1".equals(firstlogin)) {
                return "jsp/firstpd.jsp";
            }
            return "jsp/cpasswd.jsp";
        }
        userService.setUserPasswd(map);
        if (userService.isFirstLogin(uid)) {
            Map<String, Object> isfirst = new HashMap<String, Object>(16);
            isfirst.put("uid", uid);
            isfirst.put("isfirst", false);
            userService.setFirstLogin(isfirst);
            session.removeAttribute("uid");
        }
        return "fileupload";
    }

    /**
     * 更改密码入口方法
     *
     * @param model
     * @return
     */
    @RequestMapping("cpasswd")
    public String cpasswd(Model model) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        model.addAttribute("user", user);
        return "jsp/cpasswd.jsp";
    }


    @RequestMapping("getOnameBysubject")
    public @ResponseBody
    List<OrderInfo> getOnameBysubject(String subject) throws Exception {
        if (subject == null || "".equals(subject)) {
            throw new FileException("获取失败：参数错误");
        }
        return fileService.getOnameBysubject(subject);
    }

    /**
     * 文件上传方法
     *
     * @param file
     * @return
     * @throws Exception
     */
    @RequestMapping("fileup")
    public String upfileByID(MultipartFile[] file) throws Exception {
        if (file == null) {
            throw new FileException("上传失败：未获取到上传内容！");
        }
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        for (MultipartFile file1 : file) {
            if (user.getUserselect_oid() != null && !(file1.isEmpty())) {
                OrderInfo orderInfo = fileService.getOrderInfoEntityByOID(user.getUserselect_oid());
                History history = new History();
                history.setHid(UUID.randomUUID().toString().replace("-", ""));
                history.setHuid(user.getUid());
                history.setHoid(orderInfo.getOid());
                String extensionName = file1.getOriginalFilename().substring(file1.getOriginalFilename().lastIndexOf("."));
                String newfilename = user.getUsername() + user.getName() + orderInfo.getOsubject() + orderInfo.getOname() + extensionName;
                history.setFilepath(newfilename);
                history.setFilesize((double) file1.getSize());
                history.setType(file1.getContentType());
                history.setUptime(new Date());
                Map<String, Object> map = new HashMap<String, Object>(16);
                map.put("hoid", user.getUserselect_oid());
                map.put("huid", user.getUid());
                if ((fileService.findHuidExists(map)) != null) {
                    this.delEntityByHID(fileService.findHuidExists(map).getHid());
                }
                fileService.insertDataByEntity(history);
                File newfile = new File(PropertiesUtil.getUpLoadFilePath() + newfilename);
                file1.transferTo(newfile);
            }
        }
        return "index.jsp";
    }

    @RequestMapping("userselect")
    public @ResponseBody
    Boolean userSelect(@RequestParam("userselect_oid") Integer userselectOid) throws Exception {
        if (userselectOid != null) {
            User user = (User) SecurityUtils.getSubject().getPrincipal();
            user.setUserselect_oid(userselectOid);
            return user.getUserselect_oid() != null;
        }
        return false;
    }

    /**
     * 删除文件方法
     *
     * @param delHid
     * @return
     * @throws Exception
     */
    @RequestMapping("delEntityByHID")
    public @ResponseBody
    Boolean delEntityByHID(String delHid) throws Exception {
        if (delHid == null || "".equals(delHid)) {
            throw new FileException("删除失败：参数为空");
        }
        Boolean istrueuser = false;
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<History> historyList = fileService.getUpListByUID(user.getUid());
        for (History history : historyList) {
            if (history.getHid().equals(delHid)) {
                istrueuser = true;
            }
        }
        if (istrueuser) {
            History history = fileService.getEntityByHID(delHid);
            File file = new File(PropertiesUtil.getUpLoadFilePath() + history.getFilepath());
            fileService.delEntityByHID(delHid);
            //文件未被删除且存在
            return !file.exists() || file.delete();
        }
        return false;
    }

    @RequestMapping("downFile")
    public void downLoadFile(String hid, HttpServletResponse response) throws Exception {
        if (hid == null || "".equals(hid)) {
            throw new FileException("下载失败：参数为空！");
        }
        History history = fileService.getEntityByHID(hid);
        String filename = PropertiesUtil.getUpLoadFilePath() + history.getFilepath();
        File file = new File(filename);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Disposition", "attachment;filename=" + new String(history.getFilepath().getBytes(), StandardCharsets.ISO_8859_1));
        response.setHeader("Content-Length", file.length() + "");
        response.setContentType(history.getType());
        try (OutputStream toClient = new BufferedOutputStream(response.getOutputStream());
             InputStream fis = new BufferedInputStream(new FileInputStream(file))) {
            IOUtils.copy(fis, toClient);
            toClient.flush();
        } catch (IOException e) {
            if (!e.getMessage().contains("连接")) {
                throw e;
            }
        }
    }

}