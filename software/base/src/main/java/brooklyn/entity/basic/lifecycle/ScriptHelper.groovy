package brooklyn.entity.basic.lifecycle;

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ScriptHelper {

	public static final Logger log = LoggerFactory.getLogger(ScriptHelper.class);
			
	protected final ScriptRunner runner;
	protected final String summary;
	
	public final ScriptPart header = [this];
	public final ScriptPart body = [this];
	public final ScriptPart footer = [this];
	
	protected Closure resultCodeCheck = { true }
	protected Closure executionCheck = { true }
	
	public ScriptHelper(ScriptRunner runner, String summary) {
		this.runner = runner;
		this.summary = summary;
	}

	/** takes a closure which accepts this ScriptHelper,
	 * returns true or false as to whether the script needs to run
	 * (or can throw error if desired) */
	public ScriptHelper executeIf(Closure c) {
		executionCheck = c
		this
	}
	public ScriptHelper skipIfBodyEmpty() { executeIf { !it.body.isEmpty() } }
	public ScriptHelper failIfBodyEmpty() { executeIf { 
		if (it.body.isEmpty()) throw new IllegalStateException("body empty for "+summary);
		true
	} }
	
	public ScriptHelper failOnNonZeroResultCode(boolean val=true) {
		requireResultCode( val? { it==0 } : { true } )
		this
	}
	/**
	 * convenience for error-checking the result
	 * <p> 
	 * takes closure which accepts bash exit code (integer),
	 * and returns false if it is invalid; 
	 * default is that this resultCodeCheck closure always returns true 
	 * (and the exit code is made available to the caller if they care) */
	public ScriptHelper requireResultCode(Closure integerFilter) {
		resultCodeCheck = integerFilter
		this		
	}
	
	public int execute() {
		if (!executionCheck.call(this)) return 0
		if (log.isDebugEnabled()) log.debug "executing: {} - {}", summary, lines
		int result;
		try {
			result = runner.execute(lines, summary)
		} catch (InterruptedException e) {
			throw e
		} catch (Exception e) {
			throw new IllegalStateException("execution failed, invocation error for ${summary}", e)
		}
		if (log.isDebugEnabled()) log.debug "finished executing: {} - result code {}", summary, result
		if (!resultCodeCheck.call(result))
			throw new IllegalStateException("execution failed, invalid result ${result} for ${summary}")
		result		
	}
	
	public List<String> getLines() {
		header.lines+body.lines+footer.lines
	}
}

public class ScriptPart {
	protected ScriptHelper helper;
	protected List<String> lines = []
	
	public ScriptPart(ScriptHelper helper) {
		this.helper = helper;
	}
	public ScriptHelper append(String l1) {
		append([l1])
	}
	//bit ugly but 'xxx' and "xxx" mixed don't fit String...
	public ScriptHelper append(Object l1, Object l2, Object ...ll) {
		append([l1, l2] + (ll as List));
	}
	public ScriptHelper append(List ll) {
		lines.addAll(ll)
		helper
	}
	
	public ScriptHelper prepend(String l1) {
		prepend([l1])
	}
	public ScriptHelper prepend(Object l1, Object l2, Object ...ll) {
		prepend([l1, l2] + (ll as List));
	}
	public ScriptHelper prepend(List ll) {
		lines.addAll(0, ll)
		helper
	}
	
	public ScriptHelper reset(String l1) {
		reset([l1])
	}
	public ScriptHelper reset(Object l1, Object l2, Object ...ll) {
		reset([l1, l2] + (ll as List));
	}
	public ScriptHelper reset(List ll) {
		lines.clear()
		lines.addAll(ll)
		helper
	}
	/** passes the list to a closure for amendment; result of closure ignored */
	public ScriptHelper apply(Closure c) {
		c.call(lines)
		helper
	}
	public boolean isEmpty() { lines.isEmpty() }

    public static class CommonCommands {
        /** returns a string for checking whether the given executable is available,
         * and installing it if necessary, using {@link #installPackage} 
         * and accepting the same flags e.g. for apt, yum, rpm */
        public static String installExecutable(Map flags, String executable) {
            "which ${executable} || "+installPackage(flags, executable)
        }
        /** returns a string for installing the given package;
         * flags can contain common overrides e.g. for apt, yum, rpm
         * (as the package names can be different for each of those), e.g.:
         * installPackage("libssl-devel", yum: "openssl-devel", apt:"openssl libssl-dev zlib1g-dev");
         * exit code 44 used to indicate failure */
        public static String installPackage(Map flags, String packageDefaultName) {
            "(which apt-get && apt-get install ${flags.apt?:packageDefaultName}) || "+
                    "(which rpm && rpm -i ${flags.rpm?:packageDefaultName}) || "+
                    "(which yum && yum install ${flags.yum?:packageDefaultName}) || "+
                    "(echo \"No known package manager to install ${packageDefaultName}, failing\" && exit 44)"
        }
        public static final String INSTALL_TAR = installExecutable("tar");
        public static final String INSTALL_CURL = installExecutable("curl");
        public static final String INSTALL_WGET = installExecutable("wget");
    }
}