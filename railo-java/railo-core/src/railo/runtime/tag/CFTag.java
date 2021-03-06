package railo.runtime.tag;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.Tag;

import railo.commons.lang.StringUtil;
import railo.runtime.Component;
import railo.runtime.ComponentPro;
import railo.runtime.PageContext;
import railo.runtime.PageContextImpl;
import railo.runtime.component.ComponentLoader;
import railo.runtime.component.Member;
import railo.runtime.customtag.CustomTagUtil;
import railo.runtime.customtag.InitFile;
import railo.runtime.exp.ApplicationException;
import railo.runtime.exp.CasterException;
import railo.runtime.exp.ExpressionException;
import railo.runtime.exp.PageException;
import railo.runtime.exp.PageRuntimeException;
import railo.runtime.exp.PageServletException;
import railo.runtime.ext.tag.AppendixTag;
import railo.runtime.ext.tag.BodyTagTryCatchFinallyImpl;
import railo.runtime.ext.tag.DynamicAttributes;
import railo.runtime.op.Caster;
import railo.runtime.op.Decision;
import railo.runtime.type.Collection;
import railo.runtime.type.KeyImpl;
import railo.runtime.type.List;
import railo.runtime.type.Struct;
import railo.runtime.type.StructImpl;
import railo.runtime.type.scope.Caller;
import railo.runtime.type.scope.CallerImpl;
import railo.runtime.type.scope.Undefined;
import railo.runtime.type.scope.UndefinedImpl;
import railo.runtime.type.scope.Variables;
import railo.runtime.type.scope.VariablesImpl;
import railo.runtime.type.util.ArrayUtil;
import railo.runtime.type.util.ComponentUtil;
import railo.runtime.type.util.Type;
import railo.runtime.util.QueryStack;
import railo.runtime.util.QueryStackImpl;
import railo.transformer.library.tag.TagLibTag;
import railo.transformer.library.tag.TagLibTagAttr;


/**
* Creates a CFML Custom Tag
**/
public class CFTag extends BodyTagTryCatchFinallyImpl implements DynamicAttributes,AppendixTag {

	private static Collection.Key GENERATED_CONTENT=KeyImpl.intern("GENERATEDCONTENT");
	private static Collection.Key EXECUTION_MODE=KeyImpl.intern("EXECUTIONMODE");      
	private static Collection.Key EXECUTE_BODY=KeyImpl.intern("EXECUTEBODY");
	private static Collection.Key HAS_END_TAG=KeyImpl.intern("HASENDTAG");
	private static Collection.Key PARENT=KeyImpl.intern("PARENT");
	private static Collection.Key CFCATCH=KeyImpl.intern("CFCATCH");
	private static Collection.Key SOURCE=KeyImpl.intern("SOURCE");
	
	
	private static Collection.Key ATTRIBUTES=KeyImpl.intern("ATTRIBUTES");
	private static Collection.Key CALLER=KeyImpl.intern("CALLER");
	private static Collection.Key THIS_TAG=KeyImpl.intern("THISTAG");
	

	private static final Collection.Key ON_ERROR = KeyImpl.intern("onError");
	private static final Collection.Key ON_FINALLY = KeyImpl.intern("onFinally");
	private static final Collection.Key ON_START_TAG = KeyImpl.intern("onStartTag");
	private static final Collection.Key ON_END_TAG = KeyImpl.intern("onEndTag");
	private static final Collection.Key INIT = KeyImpl.intern("init");

	private static final Collection.Key ATTRIBUTE_TYPE = KeyImpl.intern("attributetype");
	private static final Collection.Key RT_EXPR_VALUE = KeyImpl.intern("rtexprvalue");
	private static final Collection.Key PARSE_BODY = KeyImpl.intern("parsebody");
	private static final Collection.Key METADATA = KeyImpl.intern("metadata");
	private static final String MARKER = "2w12801";
	
    /**
     * Field <code>attributesScope</code>
     */
    // new scopes
    protected StructImpl attributesScope;
    private Caller callerScope;
    private StructImpl thistagScope;

    private Variables ctVariablesScope;

    private boolean hasBody;

    /**
     * Field <code>filename</code>
     */
    //protected String filename;

    /**
     * Field <code>source</code>
     */
    protected InitFile source;
    private String appendix;
	//private boolean doCustomTagDeepSearch;
	
	private ComponentPro cfc;
	private boolean isEndTag;
	
	
	
    /**
    * constructor for the tag class
    **/
    public CFTag() {
    	attributesScope = new StructImpl();
        callerScope = new CallerImpl();
        //thistagScope = new StructImpl();
    }

    /**
     * @see railo.runtime.ext.tag.DynamicAttributes#setDynamicAttribute(java.lang.String, java.lang.String, java.lang.Object)
     */
    public void setDynamicAttribute(String uri, String name, Object value) {
    	TagUtil.setDynamicAttribute(attributesScope,name,value);
    }

    /**
    * @see javax.servlet.jsp.tagext.Tag#release()
    */
    public void release()   {
        super.release();

        hasBody=false;
        //filename=null;      

        attributesScope=new StructImpl();//.clear();
        callerScope = new CallerImpl();
        if(thistagScope!=null)thistagScope=null;
        if(ctVariablesScope!=null)ctVariablesScope=null;  
        

        isEndTag=false;     
        
        //cfc=null;
        source=null;
    }

    /**
     * sets the appendix of the class
     * @param appendix
     */
    public void setAppendix(String appendix) {
        this.appendix=appendix;
        //filename = appendix+'.'+pageContext.getConfig().getCFMLExtension();
    }

    /**
    * @see javax.servlet.jsp.tagext.Tag#doStartTag()
    */
    public int doStartTag() throws PageException    {
    	PageContextImpl pci=(PageContextImpl) pageContext;
		boolean old=pci.useSpecialMappings(true);
		try{
			initFile();
	    	callerScope.initialize(pageContext);
	        if(source.isCFC())return cfcStartTag();
	        return cfmlStartTag();	
		}
		finally{
			pci.useSpecialMappings(old);
		}
    	
    	
    	
    	
    }

    /**
    * @see javax.servlet.jsp.tagext.Tag#doEndTag()
    */
    public int doEndTag()   {
    	PageContextImpl pci=(PageContextImpl) pageContext;
		boolean old=pci.useSpecialMappings(true);
		try{
			if(source.isCFC())_doCFCFinally();
	        return EVAL_PAGE;
		}
		finally{
			pci.useSpecialMappings(old);
		}
    }

    /**
    * @see javax.servlet.jsp.tagext.BodyTag#doInitBody()
    */
    public void doInitBody()    {
        
    }

    /**
    * @see javax.servlet.jsp.tagext.BodyTag#doAfterBody()
    */
    public int doAfterBody() throws PageException   {
    	if(source.isCFC())return cfcEndTag();
        return cfmlEndTag();
    }
    

	/**
	 * @see railo.runtime.ext.tag.BodyTagTryCatchFinallyImpl#doCatch(java.lang.Throwable)
	 */
    public void doCatch(Throwable t) throws Throwable {
    	if(source.isCFC()){
	    	String source=isEndTag?"end":"body";
	    	isEndTag=false;
	    	_doCFCCatch(t,source);
    	}
    	else super.doCatch(t);
	}
    
    void initFile() throws PageException {
        source=initFile(pageContext);
    }

    public InitFile initFile(PageContext pageContext) throws PageException {
    	return CustomTagUtil.loadInitFile(pageContext, appendix);
    }
    
	private int cfmlStartTag() throws PageException {
		callerScope.initialize(pageContext);
        
		// thistag
		if(thistagScope==null)thistagScope=new StructImpl(StructImpl.TYPE_LINKED);
        thistagScope.set(GENERATED_CONTENT,"");
        thistagScope.set(EXECUTION_MODE,"start");      
        thistagScope.set(EXECUTE_BODY,Boolean.TRUE);
        thistagScope.set(HAS_END_TAG,Caster.toBoolean(hasBody));
        
		
		ctVariablesScope=new VariablesImpl();
        ctVariablesScope.setEL(ATTRIBUTES,attributesScope);
        ctVariablesScope.setEL(CALLER,callerScope);
        ctVariablesScope.setEL(THIS_TAG,thistagScope);
        
        
        // include
        doInclude();
        
        return Caster.toBooleanValue(thistagScope.get(EXECUTE_BODY))?EVAL_BODY_BUFFERED:SKIP_BODY;
    } 
	
    private int cfmlEndTag() throws PageException {
        // thistag     
    	String genConBefore = bodyContent.getString();
    	thistagScope.set(GENERATED_CONTENT,genConBefore);
        thistagScope.set(EXECUTION_MODE,"end");
        thistagScope.set(EXECUTE_BODY,Boolean.FALSE);
        writeEL(bodyContent, MARKER);
        
        // include
        try{
        	doInclude();
        }
        catch(Throwable t){
        	writeOut(genConBefore);
        	throw Caster.toPageException(t);
        }
        
        writeOut(genConBefore);

        return Caster.toBooleanValue(thistagScope.get(EXECUTE_BODY))?EVAL_BODY_BUFFERED:SKIP_BODY;
    }

    

	private void writeOut(String genConBefore) throws PageException {
		String output = bodyContent.getString(); 
		bodyContent.clearBody();
    	String genConAfter = Caster.toString(thistagScope.get(GENERATED_CONTENT));
    	
    	if(genConBefore!=genConAfter){
        	if(output.startsWith(genConBefore+MARKER)){
    			output=output.substring((genConBefore+MARKER).length());
    		}
    		output=genConAfter+output;
    	}
    	else {
    		if(output.startsWith(genConBefore+MARKER)){
    			output=output.substring((genConBefore+MARKER).length());
    			output=genConBefore+output;
    		}
    	}
    	
    	
    	writeEL(bodyContent.getEnclosingWriter(),output);
	}

	private void writeEL(JspWriter writer, String str) throws PageException {
		try {
			writer.write(str);
		} catch (IOException e) {
			throw Caster.toPageException(e);
		}
	}

	void doInclude() throws PageException {
        Variables var=pageContext.variablesScope();
        pageContext.setVariablesScope(ctVariablesScope);
        
        
        QueryStack cs=null;
        Undefined undefined=pageContext.undefinedScope();
        int oldMode=undefined.setMode(Undefined.MODE_NO_LOCAL_AND_ARGUMENTS);
        if(oldMode!=Undefined.MODE_NO_LOCAL_AND_ARGUMENTS)
        	callerScope.setScope(var,pageContext.localScope(),pageContext.argumentsScope(),true);
        else 
        	callerScope.setScope(var,null,null,false);
        
        if(pageContext.getConfig().allowImplicidQueryCall()) {
            cs=undefined.getQueryStack();
            undefined.setQueryStack(new QueryStackImpl());
        }
            
        try {
            pageContext.doInclude(source.getPageSource());
        }
        catch (Throwable t) {
            throw Caster.toPageException(t);
        }
        finally {
            undefined.setMode(oldMode);
            //varScopeData=variablesScope.getMap();
            pageContext.setVariablesScope(var);
            if(pageContext.getConfig().allowImplicidQueryCall()) {
                undefined.setQueryStack(cs);
            }
        }
    
    }
    
    
    // CFC
    
    private int cfcStartTag() throws PageException {
    	
    	callerScope.initialize(pageContext);
        cfc = ComponentLoader.loadComponent(pageContext,null,source.getPageSource(), source.getFilename().substring(0,source.getFilename().length()-(pageContext.getConfig().getCFCExtension().length()+1)), false,true);
        validateAttributes(cfc,attributesScope,StringUtil.ucFirst(List.last(source.getPageSource().getComponentName(),'.')));
        
        boolean exeBody = false;
        try	{
			Object rtn=Boolean.TRUE;
			if(cfc.contains(pageContext, INIT)){
	        	Tag parent=getParent();
	        	while(parent!=null && !(parent instanceof CFTag && ((CFTag)parent).isCFCBasedCustomTag())) {
	    			parent=parent.getParent();
	    		}
				Struct args=new StructImpl(StructImpl.TYPE_LINKED);
				args.set(HAS_END_TAG, Caster.toBoolean(hasBody));
	    		if(parent instanceof CFTag) {
	    			args.set(PARENT, ((CFTag)parent).getComponent());
	    		}
	        	rtn=cfc.callWithNamedValues(pageContext, INIT, args);
	        }
			
	        if(cfc.contains(pageContext, ON_START_TAG)){
	        	Struct args=new StructImpl();
	        	args.set(ATTRIBUTES, attributesScope);
	        	setCaller(pageContext,args);
	        	
	        	
	        	
	        	rtn=cfc.callWithNamedValues(pageContext, ON_START_TAG, args);	
		    }
	        exeBody=Caster.toBooleanValue(rtn,true);
        }
        catch(Throwable t){
        	_doCFCCatch(t,"start");
        }
        return exeBody?EVAL_BODY_BUFFERED:SKIP_BODY;
    }
    
    private static void setCaller(PageContext pageContext, Struct args) throws PageException {
    	UndefinedImpl undefined=(UndefinedImpl) pageContext.undefinedScope();
    	args.set(CALLER, undefined.duplicate(false));
    	//args.set(CALLER, pageContext.variablesScope());
    	
	}

	private static void validateAttributes(ComponentPro cfc,StructImpl attributesScope,String tagName) throws ApplicationException, ExpressionException {
		
		TagLibTag tag=getAttributeRequirments(cfc,false);
		if(tag==null) return;
		
		if(tag.getAttributeType()==TagLibTag.ATTRIBUTE_TYPE_FIXED || tag.getAttributeType()==TagLibTag.ATTRIBUTE_TYPE_MIXED){
			Iterator it = tag.getAttributes().entrySet().iterator();
			Map.Entry entry;
			int count=0;
			Collection.Key key;
			TagLibTagAttr attr;
			Object value;
			// check existing attributes
			while(it.hasNext()){
				entry = (Entry) it.next();
				count++;
				key=KeyImpl.toKey(entry.getKey(),null);
				attr=(TagLibTagAttr) entry.getValue();
				value=attributesScope.get(key,null);
				if(value==null){
					if(attr.getDefaultValue()!=null){
						value=attr.getDefaultValue();
						attributesScope.setEL(key, value);
					}
					else if(attr.isRequired())
						throw new ApplicationException("attribute ["+key.getString()+"] is required for tag ["+tagName+"]");
				}
				if(value!=null) {
					if(!Decision.isCastableTo(attr.getType(),value,true)) 
						throw new CasterException(createMessage(attr.getType(), value));
				
				}
			}
			
			// check if there are attributes not supported
			if(tag.getAttributeType()==TagLibTag.ATTRIBUTE_TYPE_FIXED && count<attributesScope.size()){
				Collection.Key[] keys = attributesScope.keys();
				for(int i=0;i<keys.length;i++){
					if(tag.getAttribute(keys[i].getLowerString())==null)
						throw new ApplicationException("attribute ["+keys[i].getString()+"] is not supported for tag ["+tagName+"]");
				}
				
			 	//Attribute susi is not allowed for tag cfmail
			}
		}
	}
    
    private static String createMessage(String type, Object value) {
    	if(value instanceof String) return "can't cast String ["+value+"] to a value of type ["+type+"]";
    	else if(value!=null) return "can't cast Object type ["+Type.getName(value)+"] to a value of type ["+type+"]";
		else return "can't cast Null value to value of type ["+type+"]";

    } 
    

	private static TagLibTag getAttributeRequirments(ComponentPro cfc, boolean runtime) throws ExpressionException {
		Struct meta=null;
    	//try {
    		//meta = Caster.toStruct(cfc.get(Component.ACCESS_PRIVATE, METADATA),null,false);
    		Member mem = ComponentUtil.toComponentAccess(cfc).getMember(Component.ACCESS_PRIVATE, METADATA,true,false);
    		if(mem!=null)meta = Caster.toStruct(mem.getValue(),null,false);
		//}catch (PageException e) {e.printStackTrace();}
    	if(meta==null) return null;
    	
    	TagLibTag tag=new TagLibTag(null);
    // TAG
    	
    	// type
    	
    	String type=Caster.toString(meta.get(ATTRIBUTE_TYPE,"dynamic"),"dynamic");
    	
    	if("fixed".equalsIgnoreCase(type))tag.setAttributeType(TagLibTag.ATTRIBUTE_TYPE_FIXED);
    	//else if("mixed".equalsIgnoreCase(type))tag.setAttributeType(TagLibTag.ATTRIBUTE_TYPE_MIXED);
    	//else if("noname".equalsIgnoreCase(type))tag.setAttributeType(TagLibTag.ATTRIBUTE_TYPE_NONAME);
    	else tag.setAttributeType(TagLibTag.ATTRIBUTE_TYPE_DYNAMIC);
    	
    	if(!runtime){
    		// hint
    		String hint=Caster.toString(meta.get(KeyImpl.HINT,null),null);
    		if(!StringUtil.isEmpty(hint))tag.setDescription(hint);
    		
    		// parseBody
    		boolean rtexprvalue=Caster.toBooleanValue(meta.get(PARSE_BODY,Boolean.FALSE),false);
    		tag.setParseBody(rtexprvalue);
    	}
    	
    // ATTRIBUTES
    	Struct attributes=Caster.toStruct(meta.get(ATTRIBUTES,null),null,false);
    	if(attributes!=null) {
    		Iterator it = attributes.entrySet().iterator();
    		Map.Entry entry;
    		TagLibTagAttr attr;
    		Struct sct;
    		String name;
    		Object defaultValue;
    		while(it.hasNext()){
    			entry=(Entry) it.next();
    			name=Caster.toString(entry.getKey(),null);
    			if(StringUtil.isEmpty(name)) continue;
    			attr=new TagLibTagAttr(tag);
    			attr.setName(name);
    			
    			sct=Caster.toStruct(entry.getValue(),null,false);
    			if(sct!=null){
    				attr.setRequired(Caster.toBooleanValue(sct.get(KeyImpl.REQUIRED,Boolean.FALSE),false));
    				attr.setType(Caster.toString(sct.get(KeyImpl.TYPE,"any"),"any"));
    				
    				defaultValue= sct.get(KeyImpl.DEFAULT,null);
    				if(defaultValue!=null)attr.setDefaultValue(defaultValue);
    				
    				
    				if(!runtime){
    					attr.setDescription(Caster.toString(sct.get(KeyImpl.HINT,null),null));
    					attr.setRtexpr(Caster.toBooleanValue(sct.get(RT_EXPR_VALUE,Boolean.TRUE),true));
    				}
    			}
    			tag.setAttribute(attr);
    			
    		}
    	}
    	return tag;
	}

	private int cfcEndTag() throws PageException {
        
    	boolean exeAgain = false;
        try{
	    	String output=null;
	    	Object rtn=Boolean.FALSE;
	        
	    	
	    	if(cfc.contains(pageContext, ON_END_TAG)){
	    		try {
		        	output=bodyContent.getString();
		            bodyContent.clearBody();
		            //rtn=cfc.call(pageContext, ON_END_TAG, new Object[]{attributesScope,pageContext.variablesScope(),output});
		            
		            Struct args=new StructImpl(StructImpl.TYPE_LINKED);
		        	args.set(ATTRIBUTES, attributesScope);
		        	setCaller(pageContext, args);
		        	args.set(GENERATED_CONTENT, output);
		        	rtn=cfc.callWithNamedValues(pageContext, ON_END_TAG, args);	
			    
		            
		            
	        	}
	        	finally	{
	        		writeEnclosingWriter();
	        	}
	        }
	    	else writeEnclosingWriter();
	    	
	        exeAgain= Caster.toBooleanValue(rtn,false);
	    }
        catch(Throwable t){
        	isEndTag=true;
        	throw Caster.toPageException(t);
        }
        return exeAgain?EVAL_BODY_BUFFERED:SKIP_BODY;
    	
    }
    
    public void _doCFCCatch(Throwable t, String source) throws PageException {
    	writeEnclosingWriter();
    	
    	// remove PageServletException wrap
    	if(t instanceof PageServletException) {
		    PageServletException pse=(PageServletException)t;
		    t=pse.getPageException();
		}
    	
    	// abort 
    	try {
			if(t instanceof railo.runtime.exp.Abort){
				if(bodyContent!=null){
					bodyContent.writeOut(bodyContent.getEnclosingWriter());
					bodyContent.clearBuffer();
				}
				throw Caster.toPageException(t);
			}
		}
		catch(IOException ioe){
			throw Caster.toPageException(ioe);
		}
    	
    	
    	
    	try {
			if(cfc.contains(pageContext, ON_ERROR)){
	        	PageException pe = Caster.toPageException(t);
	        	//Object rtn=cfc.call(pageContext, ON_ERROR, new Object[]{pe.getCatchBlock(pageContext),source});
	    		
	        	Struct args=new StructImpl(StructImpl.TYPE_LINKED);
	        	args.set(CFCATCH, pe.getCatchBlock(pageContext));
	        	args.set(SOURCE, source);
	        	Object rtn=cfc.callWithNamedValues(pageContext, ON_ERROR, args);	
		    
	        	if(Caster.toBooleanValue(rtn,false))
					throw t;
	        }
			else throw t;
    	}
    	catch(Throwable th) {
    		writeEnclosingWriter();
    		_doCFCFinally();
    		throw Caster.toPageException(th);
    	}
    	writeEnclosingWriter();
	}
    
    private void _doCFCFinally() {
		if(cfc.contains(pageContext, ON_FINALLY)){
			try {
				cfc.call(pageContext, ON_FINALLY, ArrayUtil.OBJECT_EMPTY);
			} 
			catch (PageException pe) {
				throw new PageRuntimeException(pe);
			}
			finally{
				writeEnclosingWriter();
			}
        }
	}
    
    
    private void writeEnclosingWriter()  {
    	if(bodyContent!=null){
			try {
				String output = bodyContent.getString(); 
				bodyContent.clearBody();
	            bodyContent.getEnclosingWriter().write(output);    
	        } 
			catch (IOException e) {
	        	//throw Caster.toPageException(e);
	        }
    	}
	}
    
    

    /**
     * sets if tag has a body or not
     * @param hasBody
     */
    public void hasBody(boolean hasBody) {
    	this.hasBody=hasBody;
    }

    /**
     * @return Returns the appendix.
     */
    public String getAppendix() {
        return appendix;
    }

    /**
     * @return return thistag
     */
    public Struct getThis() {
    	if(isCFCBasedCustomTag()){
    		return cfc;
    	}
        return thistagScope;
    }
    
    /**
     * @return return thistag
     */
    public Struct getCallerScope() {
        return callerScope;
    }
    
    /**
     * @return return thistag
     */
    public Struct getAttributesScope() {
        return attributesScope;
    }

	/**
	 * @return the ctVariablesScope
	 */
	public Struct getVariablesScope() {
		if(isCFCBasedCustomTag())	{
			return cfc.getComponentScope();
		}
		return ctVariablesScope;
	}
	
    /**
	 * @return the cfc
	 */
	public Component getComponent() {
		return cfc;
	}
	
	public boolean isCFCBasedCustomTag() {
		return getSource().isCFC();
	}
	
	private InitFile getSource() {
		if(source==null){
			try {
				source=initFile(pageContext);
			} catch (PageException e) {
				e.printStackTrace();
			}
		}
		return source;
	}

	/*class InitFile {
		PageSource ps;
		String filename;
		boolean isCFC;

		public InitFile(PageSource ps,String filename,boolean isCFC){
			this.ps=ps;
			this.filename=filename;
			this.isCFC=isCFC;
		}
	}*/
	
	
}