package com.jlfex.hermes.main;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSONObject;
import com.jlfex.hermes.common.App;
import com.jlfex.hermes.common.AppUser;
import com.jlfex.hermes.common.Logger;
import com.jlfex.hermes.common.Result;
import com.jlfex.hermes.common.Result.Type;
import com.jlfex.hermes.common.cache.Caches;
import com.jlfex.hermes.common.constant.HermesConstants;
import com.jlfex.hermes.common.utils.Calendars;
import com.jlfex.hermes.common.utils.MoneyUtil;
import com.jlfex.hermes.common.utils.Strings;
import com.jlfex.hermes.model.BankAccount;
import com.jlfex.hermes.model.CreditRepayPlan;
import com.jlfex.hermes.model.CrediteInfo;
import com.jlfex.hermes.model.Dictionary;
import com.jlfex.hermes.model.Invest;
import com.jlfex.hermes.model.InvestProfit;
import com.jlfex.hermes.model.Loan;
import com.jlfex.hermes.model.LoanAuth;
import com.jlfex.hermes.model.LoanLog;
import com.jlfex.hermes.model.Repay;
import com.jlfex.hermes.model.User;
import com.jlfex.hermes.model.UserAccount;
import com.jlfex.hermes.model.UserProperties;
import com.jlfex.hermes.model.yltx.FinanceOrder;
import com.jlfex.hermes.model.yltx.FinanceRepayPlan;
import com.jlfex.hermes.service.BankAccountService;
import com.jlfex.hermes.service.CreditInfoService;
import com.jlfex.hermes.service.CreditRepayPlanService;
import com.jlfex.hermes.service.DictionaryService;
import com.jlfex.hermes.service.InvestProfitService;
import com.jlfex.hermes.service.InvestService;
import com.jlfex.hermes.service.LabelService;
import com.jlfex.hermes.service.LoanService;
import com.jlfex.hermes.service.ProductService;
import com.jlfex.hermes.service.PropertiesService;
import com.jlfex.hermes.service.RepayService;
import com.jlfex.hermes.service.UserInfoService;
import com.jlfex.hermes.service.UserService;
import com.jlfex.hermes.service.finance.FinanceOrderService;
import com.jlfex.hermes.service.financePlan.FinanceRepayPlanService;
import com.jlfex.hermes.service.pojo.InvestInfo;
import com.jlfex.hermes.service.pojo.LoanUserInfo;
import com.jlfex.hermes.service.pojo.yltx.response.OrderPayResponseVo;
import com.jlfex.hermes.service.userProperties.UserPropertiesService;

@Controller
@RequestMapping("/invest")
public class InvestController {

	/** 理财业务接口 */
	@Autowired
	private LoanService loanService;
	@Autowired
	private ProductService productService;
	@Autowired
	private InvestService investService;
	@Autowired
	private InvestProfitService investProfitService;
	@Autowired
	private UserInfoService userInfoService;
	/** 系统属性业务接口 */
	@Autowired
	private PropertiesService propertiesService;
	@Autowired
	private RepayService repayService;
	@Autowired
	private DictionaryService dictionaryService;
	@Autowired
	private LabelService labelService;
	@Autowired
	private CreditInfoService creditInfoService;
	@Autowired
	private CreditRepayPlanService creditRepayPlanService;
    @Autowired
    private FinanceOrderService  financeOrderService ;
    @Autowired
    private FinanceRepayPlanService  financeRepayPlanService ;
    @Autowired
    private BankAccountService bankAccountService;
    @Autowired
    private UserPropertiesService userPropertiesService;
    @Autowired
    private UserService userService;
	
	// 正在招标中的Cache的info
	private static final String CACHE_LOAN_DEADLINE_PREFIX = "com.jlfex.hermes.cache.loan.deadline.";

	// private static final String INVEST_BID_MULTIPLE = "invest.bid.multiple";

	@RequestMapping("checkMoneyMore")
	@ResponseBody
	public JSONObject checkMoneyMore(BigDecimal investamount, String loanid) {
		Logger.info("investamount:" + investamount + "loanid:" + loanid);
		Loan loan = loanService.loadById(loanid);
		BigDecimal remain = loan.getAmount().subtract(loan.getProceeds());
		Logger.info("Remain:" + remain);
		JSONObject jsonObj = new JSONObject();
		// 大于返回false提示不成功信息
		if (investamount.compareTo(remain) == 1) {
			jsonObj.put("investamount", false);
		} else {
			jsonObj.put("investamount", true);
		}
		return jsonObj;
	}

	@RequestMapping("checkMoneyLess")
	@ResponseBody
	public JSONObject checkMoneyLess(BigDecimal investamount,String loanid) {
		Logger.info("loanId = "+loanid+",投标金额:" + investamount);
		AppUser curUser = App.current().getUser();
		UserAccount userAccount = userInfoService.loadByUserIdAndType(curUser.getId(), UserAccount.Type.CASH);
		Loan loan = loanService.loadById(loanid);
		BigDecimal balance = userAccount.getBalance();
		Logger.info("用户账户余额:" + balance);
		JSONObject jsonObj = new JSONObject();
		if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(loan.getLoanKind())){
			jsonObj.put("investamount", true);
			return jsonObj;
		}
		// 大于返回false提示不成功信息
		if (investamount.compareTo(balance) == 1) {
			jsonObj.put("investamount", false);
		} else {
			jsonObj.put("investamount", true);
		}
		return jsonObj;
	}

	/**
	 * @param mode
	 * @return
	 */
	@RequestMapping("/display")
	public String display(Model model) {
		App.checkUser();
		List<Dictionary> loanPurposeList = dictionaryService.findByTypeCode("loan_purpose");
		model.addAttribute("loanpurposes", loanPurposeList);
		List<Repay> repayList = repayService.findAll();
		model.addAttribute("repays", repayList);
		model.addAttribute("nav", "invest");
		return "invest/display";
	}

	/**
	 * 索引
	 * 
	 * @param model
	 * @return
	 */
	@RequestMapping("/index")
	public String index(Model model) {
		String page = "0", size = "8";
		model.addAttribute("purposes", dictionaryService.findByTypeCode("loan_purpose"));
		model.addAttribute("repays", repayService.findAll());
		model.addAttribute("nav", IndexController.HomeNav.INVEST);
		model.addAttribute("loans", investService.investIndexLoanList(page, size, Loan.LoanKinds.NORML_LOAN));
		return "invest/index";
	}

	@RequestMapping("/indexnormalloanfgt")
	public String indexNormalLoanFgt(@RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "10") String size, Model model) {
		model.addAttribute("normalloan", investService.investIndexLoanList(page, size, Loan.LoanKinds.NORML_LOAN));
		return "invest/indexNormalLoanFgt";
	}

	@RequestMapping("/indexassignloanfgt")
	public String indexAssignLoanFgt(@RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "10") String size, Model model) {
		model.addAttribute("assignLoan", investService.investIndexLoanList(page, size, Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN));
		return "invest/indexAssignLoanFgt";
	}

	/**
	 * 投标操作
	 * @param request
	 * @param model
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping("/bid")
	@ResponseBody
	public Result bid(String loanid, String investamount, String otherrepayselect) throws Exception {
		Result result = new Result();
		try {
			App.checkUser();
		} catch (Exception ex) {
			result.setType(Type.WARNING);
			return result;
		}
		AppUser curUser = App.current().getUser();
		User user = userInfoService.findByUserId(curUser.getId());
		Logger.info("投标操作: loanid:" + loanid + ",investamount:" + investamount + ",otherrepayselect :" + otherrepayselect);
		// 自己发布的借款 不能自己投标
		boolean bidAuthentication = investService.bidAuthentication(loanid, user);
		if (!bidAuthentication) {
			result.setType(Type.FAILURE);
			result.setData("自己发布的借款 不能自己投标");
			return result;
		}
		Map<String, String> bidResult = null;
		try {
			bidResult = investService.bid(loanid, user, new BigDecimal(investamount), otherrepayselect);
		} catch (Exception e) {
			Logger.error("投标异常：",e);
			result.setType(Type.FAILURE);
			result.addMessage(0, e.getMessage());
			return result;
		}
		if (HermesConstants.CODE_00.equals(bidResult.get("code"))) {
			result.setType(Type.SUCCESS);
			if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(bidResult.get("loanKind"))){
				result.addMessage(0, bidResult.get("orderStatus"));
				result.addMessage(1, bidResult.get("payStatus"));
			}
		} else {
			result.setType(Type.FAILURE);
			if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(bidResult.get("loanKind"))){
				result.addMessage(0, bidResult.get("msg"));
			}
		}
		return result;
	}
	/**
	 * 易联标 投标支付页面
	 * @param loanid
	 * @param investamount
	 * @param model
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping("/goJlfexBid")
	public String goJlfexBid(String loanid, String investamount, Model model) throws Exception {
		App.checkUser();
		BankAccount bankAccount = null;
		List<BankAccount> bankAccountList = bankAccountService.findByUserIdAndStatus(App.current().getUser().getId(), BankAccount.Status.ENABLED);
		//校验 银行卡绑定认证
		if(bankAccountList == null || bankAccountList.size() != 1){
			 Logger.info("投标异常：没有找到理财人有效的银行卡信息");
		}else{
			 bankAccount = bankAccountList.get(0);
			 if(Strings.empty(bankAccount.getName()) ||
				Strings.empty(bankAccount.getBank().getName()) ||
				Strings.empty(bankAccount.getDeposit()) ||
				Strings.empty(bankAccount.getAccount()) ){
				 Logger.info("银行卡绑定认证：信息不完整 或没有通过绑卡认证。");
				 bankAccount = null;
			 }
		}
		//校验 实名制认证 
		UserProperties  userProperties = userPropertiesService.queryByUser(App.current().getUser().getId());
		if(userProperties == null ||
		   Strings.empty(userProperties.getId()) ||
		   Strings.empty(userProperties.getIdType()) ){
			 Logger.info("实名制认证：信息不完整 或没有通过实名制认证。");
			userProperties = null;
		}
		model.addAttribute("nav", "invest");
		model.addAttribute("bankAccount", bankAccount);
		model.addAttribute("userProperties", userProperties);
		model.addAttribute("investAmount", investamount);
		model.addAttribute("investAmountChinese", MoneyUtil.amountToChinese(Double.parseDouble(investamount)));
		model.addAttribute("loanId", loanid);
		return "invest/bidAndPay";
	}
	/**
	 * 易联标：投标操作
	 * @param loanId
	 * @param investAmount
	 * @param model
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/jlfexBid")
	public String jlfexBid(String loanId, String investAmount, Model model) throws Exception {
		App.checkUser();
		String resultView = "";
		AppUser curUser = App.current().getUser();
		User user = userInfoService.findByUserId(curUser.getId());
		Logger.info("投标操作: loanid:" + loanId + ",investamount:" + investAmount );
		OrderPayResponseVo responseVo =  investService.createJlfexOrder(loanId, user, new BigDecimal(investAmount.trim()));
		boolean bidResult = investService.jlfexBid(loanId, user, new BigDecimal(investAmount.trim()), responseVo);
		if(bidResult){
			resultView = "invest/bidAndPaySuc";
		}else{
			resultView = "invest/bidAndPayFail";
		}
		return resultView;
	}
	
    /**
     * 
     * @param investamount
     * @param loanid
     * @param model
     * @return
     */
	@RequestMapping("/bidsuccess")
	public String bidsuccess(String investamount, String loanid, Model model) {
		App.checkUser();
		model.addAttribute("nav", "invest");
		model.addAttribute("investamount", investamount);
		model.addAttribute("loanid", loanid);
		return "invest/bidsuccess";
	}
	
	

	@RequestMapping("/bidfull")
	public String bidfull(Model model) {
		App.checkUser();
		model.addAttribute("nav", "invest");
		return "invest/bidfull";
	}

	/**
	 * 借款标不能自己投标
	 * 
	 * @param model
	 * @return
	 */
	@RequestMapping("/bidInvalid")
	public String bidInvalid(Model model) {
		App.checkUser();
		model.addAttribute("nav", "invest");
		return "invest/bidInvalid";
	}

	/**
	 * 计算预期收益
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping("/calmaturegain")
	@ResponseBody
	public BigDecimal calmaturegain(HttpServletRequest request)  throws Exception{
		BigDecimal maturegain =  BigDecimal.ZERO;
		String loanid = request.getParameter("loanid");
		String investamount = request.getParameter("investamount");
		Logger.info("计算预期收益:loanid=" + loanid + ", investamount=" + investamount);
		Loan loan = loanService.loadById(loanid);
		if(Loan.LoanKinds.NORML_LOAN.equals(loan.getLoanKind())){
			 maturegain = repayService.getRepayMethod(loan.getRepay().getId()).getProceeds(loan, null, new BigDecimal(investamount));
		}else if(Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN.equals(loan.getLoanKind())){
			 maturegain = calcuOutCreditProfit(new BigDecimal(investamount), loan);
		}else if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(loan.getLoanKind())){
			maturegain = calcuYltxCreditProfit(new BigDecimal(investamount), loan);
		}else{
			throw new  Exception("无效的标类型loanKind="+loan.getLoanKind());
		}
		return maturegain;
	}
	/**
	 * 债权标： 到期收益
	 * @param investAmount
	 * @param loan
	 * @return
	 * @throws Exception
	 */
	public BigDecimal calcuOutCreditProfit(BigDecimal investAmount, Loan loan) throws Exception {
		BigDecimal profitAmount =  BigDecimal.ZERO;
		CrediteInfo creditInfo = creditInfoService.findByLoanInfo(loan);
		List<CreditRepayPlan>  creditRepayPlanList = creditRepayPlanService.findByCreditInfoAscPeriod(creditInfo);
		for(CreditRepayPlan plan : creditRepayPlanList) {
			if(CreditRepayPlan.Status.ALREADY_PAY.equals(plan.getStatus())){
				continue;
			}
			profitAmount = plan.getRepayAllmount().add(profitAmount);
		}
		BigDecimal scale = investAmount.divide(loan.getAmount(), 8, RoundingMode.HALF_DOWN);
		return  profitAmount.multiply(scale).setScale(2, RoundingMode.HALF_UP);
	}
	/**
	 * 易联债权标: 到期收益计算
	 * @param investAmount
	 * @param loan
	 * @return
	 * @throws Exception
	 */
	public BigDecimal calcuYltxCreditProfit(BigDecimal investAmount, Loan loan) throws Exception {
		BigDecimal profitAmount =  BigDecimal.ZERO;
		FinanceOrder financeOrder = financeOrderService.queryById(loan.getCreditInfoId());
		List<FinanceRepayPlan>  creditRepayPlanList = financeRepayPlanService.queryByFinanceOrder(financeOrder);
		if(creditRepayPlanList == null || creditRepayPlanList.size() == 0){
			throw new Exception("理财产品id="+financeOrder.getUniqId()+", 对应的理财产品还款计划为空!");
		}
		for(FinanceRepayPlan plan: creditRepayPlanList){
			if(plan!=null){
				profitAmount = plan.getRepaymentMoney().add(profitAmount);
			}
		}
		BigDecimal scale = investAmount.divide(loan.getAmount(), 8, RoundingMode.HALF_DOWN);
		return  profitAmount.multiply(scale).setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * 借款列表查询
	 * @param purpose
	 * @param raterange
	 * @param periodrange
	 * @param repayname
	 * @param page
	 * @param size
	 * @param orderByField
	 * @param orderByDirection
	 * @param model
	 * @return
	 */
	@RequestMapping("/indexsearch")
	public String indexsearch(String purpose, String raterange, String periodrange, String repayname, String page, String size, String orderByField, String orderByDirection, String loanKind, Model model) {
		Logger.info("理财列表查询参数: purpose=" + getUTFFormat(purpose) + ",raterange=" + getUTFFormat(raterange) + ",periodrange=" + getUTFFormat(periodrange) + ",repayname=" + getUTFFormat(repayname) + ",loanKind=" + getUTFFormat(loanKind));
		model.addAttribute("loans", investService.findByJointSql(getUTFFormat(purpose), getUTFFormat(raterange), getUTFFormat(periodrange), getUTFFormat(repayname), page, size, orderByField, orderByDirection, loanKind));
		return "invest/loandata";
	}

	/**
	 * 把乱码解析成中文
	 * 
	 * @param src
	 * @return
	 */
	private String getUTFFormat(String src) {
		if (!Strings.empty(src)) {
			String dest = "";
			try {
				dest = new String(src.getBytes("iso-8859-1"), "utf-8");
			} catch (UnsupportedEncodingException e) {
				Logger.error("getUTFFormat Message Error:" + e.getMessage());
			}
			return dest;
		} else
			return "";
	}

	/**
	 * 借款明细
	 * 
	 * @param id
	 * @param model
	 * @return
	 */
	/**
	 * @param model
	 * @param loanid
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping("/info")
	public String info(Model model, String loanid) throws Exception {
		try {
			App.checkUser();
		} catch (Exception e) {
			return "redirect:/userIndex/skipSignIn";
		}
		String guaranteeType = null;
		String validFlag = "00";
		Loan loan = loanService.loadById(loanid);
		if(Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN.equals(loan.getLoanKind())){
			CrediteInfo creditInfo = creditInfoService.findById(loan.getCreditInfoId());
			List<CreditRepayPlan> creditRepayList = creditRepayPlanService.queryByCreditInfo(creditInfo); // 获取回款记录
			model.addAttribute("creditInfo", creditInfo);
			model.addAttribute("creditRepayList", creditRepayList);
		}else if(Loan.LoanKinds.NORML_LOAN.equals(loan.getLoanKind())){
			// 普通标 担保方式
			Dictionary guaranteeDic = loan.getProduct().getGuarantee();
			if (guaranteeDic != null) {
				guaranteeType = guaranteeDic.getName();
			}
		}else if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(loan.getLoanKind())){
			FinanceOrder financeOrder = financeOrderService.queryById(loan.getCreditInfoId());
			model.addAttribute("financeOrder", financeOrder);
			model.addAttribute("financePlanList", financeRepayPlanService.queryByFinanceOrder(financeOrder));
			if(!investService.checkValid(loan)){
				validFlag = "01"; 
				model.addAttribute("tipMsg", "提示：债权标有效投标时间为["+Calendars.format(HermesConstants.FORMAT_10, financeOrder.getRaiseStartTime())+"—"+Calendars.format(HermesConstants.FORMAT_10,financeOrder.getRaiseEndTime())+"]"); 
			}
		}
		AppUser curUser = App.current().getUser();
		boolean bidAuthentication = investService.bidAuthentication(loanid, userInfoService.findByUserId(curUser.getId()));
		if(!bidAuthentication){
			validFlag = "02";
			model.addAttribute("tipMsg", "提示：不能对自己发布的借款标进行投标"); 
		}
		model.addAttribute("loan", loan);
		Map<String, Object> calculateMap = calculateRemainTime(loan);
		model.addAttribute("purpose", calculateMap.get("loanPurpose"));
		model.addAttribute("remaintime", calculateMap.get("remaintime"));
		model.addAttribute("product", loan.getProduct());
		model.addAttribute("repay", loan.getProduct().getRepay());
		model.addAttribute("user", loan.getUser());
		LoanUserInfo loanUserInfo = loanService.loadLoanUserInfoByUserId(App.user().getId());
		model.addAttribute("loanUserInfo", loanUserInfo);
		List<Invest> investList = investService.findByLoan(loan);
		model.addAttribute("invests", investList);
		List<LoanAuth> loanAuthlist = loanService.findLoanAuthByLoan(loan);
		model.addAttribute("loanauths", loanAuthlist);
		model.addAttribute("nav", "invest");
		// 读取投标金额倍数设置
		String investBidMultiple = "0";
		try {
			investBidMultiple = "" + loan.getProduct().getStartingAmt().intValue();
		} catch (Exception e) {
			Logger.error("获取产品起投金额:异常", e);
		}
		model.addAttribute("investBidMultiple", investBidMultiple);
		model.addAttribute("validFlag", validFlag); // 投标是否有效标识
		model.addAttribute("guaranteeType", guaranteeType);
		model.addAttribute("loanKind", loan.getLoanKind());
		return "invest/info";
	}

	/**
	 * 计算招标截止日 剩余时间
	 * 
	 * @param loan
	 * @return
	 * @throws Exception 
	 */
	public Map<String, Object> calculateRemainTime(Loan loan) throws Exception {
		String loanPurpose = "";
		String remaintime = "0";
		Map<String, Object> paramMap = new HashMap<String, Object>();
		if(Loan.LoanKinds.NORML_LOAN.equals(loan.getLoanKind())) {
			//普通标
			loanPurpose = dictionaryService.loadById(loan.getPurpose()).getName();
			// 从借款日志表里取开始投标的起始时间
			if (Caches.get(CACHE_LOAN_DEADLINE_PREFIX + loan.getId()) == null) {
				LoanLog loanLogStartInvest = loanService.loadLogByLoanIdAndType(loan.getId(), LoanLog.Type.START_INVEST);
				if (loanLogStartInvest != null && loanLogStartInvest.getDatetime() != null) {
					String duration = String.valueOf(loan.getDeadline()) + "d";
					Date datetimeloanLogStartInvest = loanLogStartInvest.getDatetime();
					Date deadline = Calendars.add(datetimeloanLogStartInvest, duration);
					Caches.set(CACHE_LOAN_DEADLINE_PREFIX + loan.getId(), deadline, "7d");
				}
			}
			if (Caches.get(CACHE_LOAN_DEADLINE_PREFIX + loan.getId()) != null) {
				Date deadline = Caches.get(CACHE_LOAN_DEADLINE_PREFIX + loan.getId(), Date.class);
				Date start = new Date();
				long endTime = deadline.getTime();
				long startTime = start.getTime();
				if (endTime - startTime > 0) {
					remaintime = String.valueOf(endTime - startTime);
				} else {
					remaintime = "0";
				}
			} else {
				remaintime = "0";
			}
		}else if(Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN.equals(loan.getLoanKind())){
			//导入标
			loanPurpose = loan.getPurpose();
			CrediteInfo crediteInfo = creditInfoService.findById(loan.getCreditInfoId());
			try {
				long endTime = crediteInfo.getBidEndTime().getTime();
				long startTime = new Date().getTime();
				if (endTime - startTime > 0) {
					remaintime = String.valueOf(endTime - startTime);
				}
			} catch (Exception e) {
				Logger.error("导入债权计算剩余时间异常", e);
				remaintime = "0";
			}
		}else if(Loan.LoanKinds.YLTX_ASSIGN_LOAN.equals(loan.getLoanKind())){
			//易联标
			loanPurpose = loan.getPurpose();
			FinanceOrder financeOrder = financeOrderService.queryById(loan.getCreditInfoId());
			try {
				long endTime = financeOrder.getRaiseEndTime().getTime();
				long startTime = new Date().getTime();
				if (endTime - startTime > 0) {
					remaintime = String.valueOf(endTime - startTime);
				}else{
					Logger.info("募资截止日期："+financeOrder.getRaiseEndTime()+",小于当前时间:"+Calendars.format(HermesConstants.FORMAT_19));
				}
			} catch (Exception e) {
				Logger.error("易联债权计算剩余时间异常", e);
				remaintime = "0";
			}
		}else{
			Logger.error("无效的标类型：loanKind="+loan.getLoanKind());
		}
		paramMap.put("loanPurpose", loanPurpose);
		paramMap.put("remaintime", remaintime);
		return paramMap;
	}

	/**
	 * 我的理财
	 * 
	 * @param userid
	 * @param model
	 * @return
	 */
	@RequestMapping("/myinvest")
	public String myinvest(Model model) {
		App.checkUser();
		AppUser curUser = App.current().getUser();

		User user = userInfoService.findByUserId(curUser.getId());
		// 已获收益
		BigDecimal allProfitSum = investProfitService.loadSumAllProfitByUserAndInStatus(user, new String[] { InvestProfit.Status.ALREADY, InvestProfit.Status.OVERDUE, InvestProfit.Status.ADVANCE });
		// 利息
		BigDecimal interestSum = investProfitService.loadInterestSumByUserAndInStatus(user, new String[] { InvestProfit.Status.ALREADY, InvestProfit.Status.OVERDUE, InvestProfit.Status.ADVANCE });
		// 罚息
		BigDecimal overdueInterestSum = investProfitService.loadOverdueInterestSumByUserAndInStatus(user, new String[] { InvestProfit.Status.ALREADY, InvestProfit.Status.OVERDUE, InvestProfit.Status.ADVANCE });
		List<String> loanKindList = new ArrayList<String>();
		loanKindList.add(Loan.LoanKinds.NORML_LOAN);
		List<InvestInfo> investInfoList = investService.findByUser(user, loanKindList);
		int investSuccessCount = 0;
		for (InvestInfo investInfo : investInfoList) {
			if (Invest.Status.COMPLETE.equals(investInfo.getStatus())) {
				investSuccessCount = investSuccessCount + 1;
			}

		}
		if (allProfitSum == null)
			allProfitSum = BigDecimal.ZERO;
		if (interestSum == null)
			interestSum = BigDecimal.ZERO;
		if (overdueInterestSum == null)
			overdueInterestSum = BigDecimal.ZERO;
		model.addAttribute("allProfitSum", allProfitSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("interestSum", interestSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("overdueInterestSum", overdueInterestSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("successCount", investSuccessCount);

		model.addAttribute("invests", investInfoList);
		model.addAttribute("nav", "invest");
		// 返回视图
		return "invest/myinvest";
	}

	/**
	 * 我的理财的明细
	 * 
	 * @param investid
	 * @param model
	 * @return
	 */
	@RequestMapping("/myinvestinfo/{invest}")
	public String myinvestinfo(@PathVariable("invest") String investid, HttpServletRequest request, Model model) {
		App.checkUser();
		Invest invest = investService.loadById(investid);
		// String page = request.getParameter("page");
		// String size = request.getParameter("size");
		Loan loan = invest.getLoan();
		model.addAttribute("loan", loan);
		Dictionary dictionary = dictionaryService.loadById(loan.getPurpose());
		model.addAttribute("product", loan.getProduct());
		if (dictionary == null) {
			model.addAttribute("purpose", loan.getPurpose());
		} else {
			model.addAttribute("purpose", dictionary.getName());
		}

		model.addAttribute("repay", loan.getProduct().getRepay());
		model.addAttribute("user", loan.getUser());
		model.addAttribute("investprofitinfos", investProfitService.getInvestProfitRecords(invest));
		model.addAttribute("nav", "invest");
		// 返回视图
		return "invest/myinvestinfo";
	}

	/**
	 * 我的债权
	 * 
	 * @param userid
	 * @param model
	 * @return
	 */
	@RequestMapping("/myCredit")
	public String myCredit(Model model) {
		App.checkUser();
		AppUser curUser = App.current().getUser();
		User user = userInfoService.findByUserId(curUser.getId());
		// 已获收益
		List<String> loanKinds = new ArrayList<String>();
		loanKinds.add(Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN);
		loanKinds.add(Loan.LoanKinds.YLTX_ASSIGN_LOAN);
		InvestProfit investProfit = investProfitService.sumAllProfitByAssignLoan(user, loanKinds, new String[] { InvestProfit.Status.ALREADY, InvestProfit.Status.OVERDUE, InvestProfit.Status.ADVANCE });
		BigDecimal allProfitSum = BigDecimal.ZERO;// 总收益
		BigDecimal interestSum = BigDecimal.ZERO;// 利息收益总数
		BigDecimal overdueInterestSum = BigDecimal.ZERO; // 罚息收益总数
		if (investProfit != null) {
			if (investProfit.getInterestAmount() != null) {
				interestSum = investProfit.getInterestAmount();
			}
			if (investProfit.getOverdueAmount() != null) {
				overdueInterestSum = investProfit.getOverdueAmount();
			}
			allProfitSum = allProfitSum.add(interestSum).add(overdueInterestSum);
		}
		List<String> loanKindList = new ArrayList<String>();
		loanKindList.add(Loan.LoanKinds.OUTSIDE_ASSIGN_LOAN);
		loanKindList.add(Loan.LoanKinds.YLTX_ASSIGN_LOAN);
		List<InvestInfo> investInfoList = investService.findByUser(user, loanKindList);
		int investSuccessCount = 0;
		for (InvestInfo investInfo : investInfoList){
			if (Invest.Status.COMPLETE.equals(investInfo.getStatus())) {
				investSuccessCount = investSuccessCount + 1;
			}
		}
		if (allProfitSum == null)
			allProfitSum = BigDecimal.ZERO;
		if (interestSum == null)
			interestSum = BigDecimal.ZERO;
		if (overdueInterestSum == null)
			overdueInterestSum = BigDecimal.ZERO;
		model.addAttribute("allProfitSum", allProfitSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("interestSum", interestSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("overdueInterestSum", overdueInterestSum.setScale(2, BigDecimal.ROUND_UP));
		model.addAttribute("successCount", investSuccessCount);

		model.addAttribute("invests", investInfoList);
		model.addAttribute("nav", "invest");
		// 返回视图
		return "invest/mycredit";
	}

	@RequestMapping("/registeragree")
	public String registerAgree() {
		return "agree/register";
	}

	@RequestMapping("/financeagree")
	public String financeAgree() {
		return "agree/finance";
	}

	@RequestMapping("/assdebtagree")
	public String assdebtAgree() {
		return "agree/assdebt";
	}
}
