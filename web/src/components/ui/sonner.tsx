import { Toaster as Sonner, type ToasterProps } from "sonner"

import { resolvedTheme, useTheme } from "@/app/theme"

const Toaster = ({ ...props }: ToasterProps) => {
  useTheme()

  return (
    <Sonner
      theme={resolvedTheme()}
      style={{ fontFamily: "inherit", overflowWrap: "anywhere" }}
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            "bg-background text-foreground border-border border-2 font-heading shadow-shadow rounded-base text-[13px] flex items-center gap-2.5 p-4 w-[356px] [&:has(button)]:justify-between",
          description: "font-base",
          actionButton:
            "font-base border-2 text-[12px] h-6 px-2 bg-main text-main-foreground border-border rounded-base shrink-0",
          cancelButton:
            "font-base border-2 text-[12px] h-6 px-2 bg-secondary-background text-foreground border-border rounded-base shrink-0",
          error: "bg-black text-white",
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
