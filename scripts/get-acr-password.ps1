Add-Type -AssemblyName System.Runtime.InteropServices

$target = "crpi-5yw4kk2bxbk3nj6k.cn-hangzhou.personal.cr.aliyuncs.com"

Add-Type @"
using System;
using System.Runtime.InteropServices;

public class CredReader {
    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern void CredFree(IntPtr cred);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public int Flags;
        public int Type;
        public string TargetName;
        public string Comment;
        public long LastWritten;
        public int CredentialBlobSize;
        public IntPtr CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    public static (string user, string pwd) GetCredential(string target) {
        IntPtr credPtr;
        if (CredRead(target, 1, 0, out credPtr)) {
            try {
                CREDENTIAL cred = (CREDENTIAL)Marshal.PtrToStructure(credPtr, typeof(CREDENTIAL));
                string pwd = null;
                if (cred.CredentialBlobSize > 0 && cred.CredentialBlob != IntPtr.Zero) {
                    pwd = Marshal.PtrToStringUni(cred.CredentialBlob, cred.CredentialBlobSize / 2);
                }
                return (cred.UserName, pwd);
            } finally {
                CredFree(credPtr);
            }
        }
        return (null, null);
    }
}
"@

$cred = [CredReader]::GetCredential($target)
if ($cred.user -and $cred.pwd) {
    Write-Output "Found credential for: $($cred.user)"
    $pwdLen = $cred.pwd.Length
    Write-Output "Password length: $pwdLen"
    # Write password to temp file for docker login --password-stdin
    $tempFile = "$env:TEMP\acr_pwd.txt"
    $cred.pwd | Out-File -FilePath $tempFile -Encoding ASCII -NoNewline
    Write-Output "Password written to $tempFile"
    # Try login using Start-Process with stdin
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "docker"
    $psi.Arguments = "login $target --username $($cred.user) --password-stdin"
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $proc.StandardInput.Write($cred.pwd)
    $proc.StandardInput.Close()
    $stdout = $proc.StandardOutput.ReadToEnd()
    $stderr = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    Write-Output "Docker login exit code: $($proc.ExitCode)"
    if ($stdout) { Write-Output "STDOUT: $stdout" }
    if ($stderr) { Write-Output "STDERR: $stderr" }
    # Cleanup
    Remove-Item $tempFile -ErrorAction SilentlyContinue
} else {
    Write-Output "FAILED: Could not retrieve credential from Windows Credential Manager"
    Write-Output "Docker credential helper: desktop (from config.json)"
    Write-Output "The credential exists in cmdkey but docker-credential-desktop.exe fails to retrieve it"
    Write-Output "Try: Docker Desktop > Settings > Credentials > Add ACR credentials"
    exit 1
}
